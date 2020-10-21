package one;

import sun.misc.Unsafe;

/**
 * Требуется написать LongLongMap который по произвольному long ключу хранить произвольное long значение
 * Важно: все данные (в том числе дополнительные, если их размер зависит от числа элементов) требуется хранить в выделенном заранее блоке в разделяемой памяти, адрес и размер которого передается в конструкторе
 * для доступа к памяти напрямую необходимо (и достаточно) использовать следующие два метода:
 * sun.misc.Unsafe.getLong(long), sun.misc.Unsafe.putLong(long, long)
 */
public class LongLongMap {

    /**
     * @param unsafe  для доступа к памяти
     * @param address адрес начала выделенной области памяти
     * @param size    размер выделенной области в байтах (~100GB)
     */
    LongLongMap(Unsafe unsafe, long address, long size) {
        throw new UnsupportedOperationException();
    }

    /**
     * Метод должен работать со сложностью O(1) при отсутствии коллизий, но может деградировать при их появлении
     *
     * @param k произвольный ключ
     * @param v произвольное значение
     * @return предыдущее значение или 0
     */
    long put(long k, long v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Метод должен работать со сложностью O(1) при отсутствии коллизий, но может деградировать при их появлении
     *
     * @param k ключ
     * @return значение или 0
     */
    long get(long k) {
        throw new UnsupportedOperationException();
    }
}