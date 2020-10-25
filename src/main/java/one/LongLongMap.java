package one;

import sun.misc.Unsafe;

import java.util.StringJoiner;
import static one.RecordMetaUtil.*;

/**
 * Требуется написать LongLongMap который по произвольному long ключу хранить произвольное long значение
 * Важно: все данные (в том числе дополнительные, если их размер зависит от числа элементов) требуется хранить в выделенном заранее блоке в разделяемой памяти, адрес и размер которого передается в конструкторе
 * для доступа к памяти напрямую необходимо (и достаточно) использовать следующие два метода:
 * sun.misc.Unsafe.getLong(long), sun.misc.Unsafe.putLong(long, long)
 */
public class LongLongMap {
    private static final boolean DEBUG_TO_STRING = false;

    private static final int LONG_SIZE = 8;
    private static final int METASPACE_SIZE = LONG_SIZE * 3;
    private static final int RECORD_SIZE = LONG_SIZE * 3;
    /**
     * Во сколько раз емкость неиндексированного пространства больше, чем индексированного
     */
    private static final int LINKED_SPACE_MULTIPLIER = 2;

    private final Unsafe unsafe;
    private final long initAddress;

    /**
     * Для начала разобьем все доступное нам пространство на три области, которые мы придумали при проектировании.
     * <p>
     * При инициализации мапы необходимо установить в 0 значения всех флагов hasValue в indexedSpace.
     * Для этого мы в цикле пройдемся по индексированному пространству и запишем везде нули.
     *
     * @param unsafe  для доступа к памяти
     * @param address адрес начала выделенной области памяти
     * @param size    размер выделенной области в байтах (~100GB)
     */
    LongLongMap(Unsafe unsafe, long address, long size) {
        final long indexedSpaceSize = (size - METASPACE_SIZE) / (1 + LINKED_SPACE_MULTIPLIER);
        final long indexedSpaceCapacity = indexedSpaceSize / RECORD_SIZE;

        // Инициализируем metaspace.
        // Устанавливаем курсор в начало неиндексированного пространства
        final long linkedSpaceAddress = address + METASPACE_SIZE + indexedSpaceCapacity * RECORD_SIZE;
        unsafe.putLong(address, linkedSpaceAddress);
        // Запоминаем начало неиндексированного пространства
        unsafe.putLong(address + LONG_SIZE, linkedSpaceAddress);
        // Записываем емкость индексированного пространства
        unsafe.putLong(address + LONG_SIZE * 2, indexedSpaceCapacity);
        // Заполняем нулями метаинформацию записей индексированного пространства
        for (int i = 0; i < indexedSpaceCapacity; i++) {
            unsafe.putLong(address + METASPACE_SIZE + i * RECORD_SIZE, 0);
        }

        this.unsafe = unsafe;
        this.initAddress = address;
    }

    /**
     * Метод должен работать со сложностью O(1) при отсутствии коллизий, но может деградировать при их появлении
     *
     * @param k произвольный ключ
     * @param v произвольное значение
     * @return предыдущее значение или 0
     */
    long put(long k, long v) {
        final long recordAddress = getIndexedRecordAddressByKey(k);
        return put(k, v, recordAddress);
    }

    private long put(long k, long v, long recordAddress) {
        final long recordMeta = getRecordMeta(recordAddress);
        if (hasValue(recordMeta)) {
            final long recordKey = getRecordKey(recordAddress);
            if (recordKey == k) {
                final long oldValue = getRecordValue(recordAddress);
                unsafe.putLong(recordAddress + LONG_SIZE * 2, v);
                return oldValue;
            } else {
                // Well, we have a collision
                if (hasNext(recordMeta)) {
                    final long linkedRecordAddress = getLinkedRecordAddress(recordMeta);
                    return put(k, v, linkedRecordAddress);
                } else {
                    final long newRecordAddress = getCursorAddressAndIncrement();
                    // hasNext = true, hasValue = true, nextRecordIdx = newRecordAddress - linkedSpaceAddress / 24
                    final long newRecordIndex = (newRecordAddress - getLinkedSpaceAddress()) / RECORD_SIZE;
                    if (newRecordIndex >= getLinkedSpaceCapacity()) {
                        throw new IllegalStateException("Out of memory");
                    }
                    final long newMeta = HAS_NEXT_MASK | HAS_VALUE_MASK | newRecordIndex << 2;
                    unsafe.putLong(recordAddress, newMeta);
                    return createNewRecord(k, v, newRecordAddress);
                }
            }
        } else {
            return createNewRecord(k, v, recordAddress);
        }
    }

    private long createNewRecord(long k, long v, long address) {
        unsafe.putLong(address, HAS_VALUE_MASK);
        unsafe.putLong(address + LONG_SIZE, k);
        unsafe.putLong(address + LONG_SIZE * 2, v);
        return 0L;
    }

    /**
     * Метод должен работать со сложностью O(1) при отсутствии коллизий, но может деградировать при их появлении
     *
     * @param k ключ
     * @return значение или 0
     */
    long get(long k) {
        final long recordAddress = getIndexedRecordAddressByKey(k);
        return get(k, recordAddress);
    }

    private long getIndexedRecordAddressByKey(long key) {
        final long indexedSpaceCapacity = getIndexedSpaceCapacity();
        return initAddress + METASPACE_SIZE + (key % indexedSpaceCapacity) * RECORD_SIZE;
    }

    private long get(long k, long recordAddress) {
        final long recordMeta = getRecordMeta(recordAddress);
        if (hasValue(recordMeta)) {
            final long recordKey = getRecordKey(recordAddress);
            if (recordKey == k) {
                return getRecordValue(recordAddress);
            } else {
                if (hasNext(recordMeta)) {
                    final long linkedRecordAddress = getLinkedRecordAddress(recordMeta);
                    return get(k, linkedRecordAddress);
                } else {
                    return 0;
                }
            }
        } else {
            return 0;
        }
    }

    private long getRecordMeta(long recordAddress) {
        return unsafe.getLong(recordAddress);
    }

    private long getRecordKey(long recordAddress) {
        return unsafe.getLong(recordAddress + LONG_SIZE);
    }

    private long getRecordValue(long recordAddress) {
        return unsafe.getLong(recordAddress + LONG_SIZE * 2);
    }

    private long getLinkedRecordAddress(long recordMeta) {
        final long linkedRecordIndex = getLinkedRecordIndex(recordMeta);
        return getLinkedSpaceAddress() + linkedRecordIndex * RECORD_SIZE;
    }

    private long getLinkedSpaceCapacity() {
        return getIndexedSpaceCapacity() * LINKED_SPACE_MULTIPLIER;
    }

    private long getCursorAddressAndIncrement() {
        final long linkedSpaceCursorAddress = getCursorAddress();
        unsafe.putLong(initAddress, linkedSpaceCursorAddress + RECORD_SIZE);
        return linkedSpaceCursorAddress;
    }

    private long getCursorAddress() {
        return unsafe.getLong(initAddress);
    }

    private long getLinkedSpaceAddress() {
        return unsafe.getLong(initAddress + LONG_SIZE);
    }

    private long getIndexedSpaceCapacity() {
        return unsafe.getLong(initAddress + LONG_SIZE * 2);
    }

    /**
     * @return количество элементов в мапе
     */
    public long size() {
        final long indexedSpaceCapacity = getIndexedSpaceCapacity();
        long size = 0;
        for (int i = 0; i < indexedSpaceCapacity; i++) {
            final long recordAddress = initAddress + METASPACE_SIZE + i * RECORD_SIZE;
            if (hasValue(getRecordMeta(recordAddress))) {
                size++;
            }
        }
        return size + (getCursorAddress() - getLinkedSpaceAddress()) / RECORD_SIZE;
    }

    @Override
    public String toString() {
        final long indexedSpaceCapacity = getIndexedSpaceCapacity();
        final long linkedSpaceCapacity = getLinkedSpaceCapacity();

        final long linkedSpaceAddress = getLinkedSpaceAddress();
        final long cursorAddress = getCursorAddress();

        final StringJoiner stringJoiner = new StringJoiner(", ", "(", ")");
        for (int i = 0; i < indexedSpaceCapacity; i++) {
            final long recordAddress = initAddress + METASPACE_SIZE + i * RECORD_SIZE;
            joinRecord(stringJoiner, recordAddress);
        }
        return "LongLongMap" + stringJoiner.toString() + (DEBUG_TO_STRING ? "\n" +
                String.format("Size: %d\n", size()) +
                String.format("Init Address: %d\n", initAddress) +
                String.format("Indexed Space Address: %d\n", initAddress + METASPACE_SIZE) +
                String.format("Linked Space Address: %d\n", linkedSpaceAddress) +
                String.format("Cursor Address: %d\n", cursorAddress) +
                String.format("Linked Space Elements: %d\n", (cursorAddress - linkedSpaceAddress) / RECORD_SIZE) +
                String.format("Indexed space capacity: %d\n", indexedSpaceCapacity) +
                String.format("Linked space capacity: %d\n", linkedSpaceCapacity) +
                String.format("Overall capacity: %d - %d\n", 1 + linkedSpaceCapacity, indexedSpaceCapacity + linkedSpaceCapacity) : "");
    }

    private void joinRecord(StringJoiner stringJoiner, long recordAddress) {
        final long recordMeta = unsafe.getLong(recordAddress);
        if (hasValue(recordMeta)) {
            final long key = unsafe.getLong(recordAddress + LONG_SIZE);
            final long value = unsafe.getLong(recordAddress + LONG_SIZE * 2);

            stringJoiner.add(String.format("%d -> %d", key, value));

            if (hasNext(recordMeta)) {
                final long linkedRecordAddress = getLinkedRecordAddress(recordMeta);
                joinRecord(stringJoiner, linkedRecordAddress);
            }
        }
    }
}

class RecordMetaUtil {
    final static long HAS_VALUE_MASK = 0x0000_0000_0000_0001L;
    final static long HAS_NEXT_MASK = 0x0000_0000_0000_0002L;
    final static long NEXT_IDX_MASK = 0xFFFF_FFFF_FFFF_FFFCL;

    private RecordMetaUtil() {
        throw new UnsupportedOperationException();
    }

    static boolean hasValue(long recordMeta) {
        return (HAS_VALUE_MASK & recordMeta) != 0;
    }

    static boolean hasNext(long recordMeta) {
        return (HAS_NEXT_MASK & recordMeta) != 0;
    }

    static long getLinkedRecordIndex(long recordMeta) {
        return (recordMeta & NEXT_IDX_MASK) >> 2;
    }

}