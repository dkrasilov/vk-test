package one;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LongLongMapTest {
    private static Unsafe unsafe;

    @BeforeAll
    static void setUp() throws NoSuchFieldException, IllegalAccessException {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (Unsafe) f.get(null);
    }

    @Test
    void initWorks() {
        final long size = 1024;
        final long address = unsafe.allocateMemory(size);
        final LongLongMap longLongMap = new LongLongMap(unsafe, address, size);

        for (long i = 0; i < 1000L; i++) {
            // когда мапа пустая все геты должны вернуть нулл
            assertEquals(0L, longLongMap.get(i));
        }
    }

    @Test
    void mapWorks() {
        final long size = 1514911881L;
        final long address = unsafe.allocateMemory(size);
        final LongLongMap longLongMap = new LongLongMap(unsafe, address, size);

        for (long i = 1; i < 1000000L; i++) {
//            System.out.println(longLongMap);
            final long nTimesI = i * 100;
            final long putResult = longLongMap.put(i, nTimesI);
//            System.out.printf("Put (%d, %d) = %d\n", i, nTimesI, putResult);

            // когда мапа пустая все первые путы должны вернуть нулл
            assertEquals(0L, putResult);

            final long getResult = longLongMap.get(i);
//            System.out.printf("Get (%d) = %d \n", i, getResult);

            // геты = i * 100
            assertEquals(nTimesI, getResult);
        }
    }

    @Test
    void shouldThrowIllegalStateWhenMapIsFull() {
        final long size = 1024;
        final long address = unsafe.allocateMemory(size);
        final LongLongMap longLongMap = new LongLongMap(unsafe, address, size);

        assertThrows(IllegalStateException.class, () -> {
            for (long i = 1; i < 1000000L; i++) {
                longLongMap.put(i, i * 123);
            }
        });
    }


    @Test
    void shouldReturnOldValueOnPutWithSameKey() {
        final long size = 1024;
        final long address = unsafe.allocateMemory(size);
        final LongLongMap longLongMap = new LongLongMap(unsafe, address, size);

        assertEquals(0, longLongMap.put(1, 123));
        assertEquals(123, longLongMap.put(1, 421));
    }


}