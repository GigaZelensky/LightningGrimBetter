
package ac.grim.grimac.utils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;

/**
 * Very small circular list for statistical sampling.
 * When {@code capacity} elements are collected it reports
 * {@link #isCollected()} = true and begins overwriting the
 * oldest values.
 */
public class SampleList<E> implements Iterable<E> {

    private final int capacity;
    private final ArrayDeque<E> deque;

    public SampleList(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.deque = new ArrayDeque<>(capacity);
    }

    public void add(E element) {
        if (deque.size() == capacity) {
            deque.pollFirst();
        }
        deque.addLast(element);
    }

    public boolean isCollected() {
        return deque.size() == capacity;
    }

    public int size() {
        return deque.size();
    }

    public void clear() {
        deque.clear();
    }

    public Iterator<E> iterator() {
        return deque.iterator();
    }
}
