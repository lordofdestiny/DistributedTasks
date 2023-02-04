package rs.ac.bg.etf.kdp.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;


/**
 * Implementation of List interface, ReadWriteList is a list container
 * implemented in a way that solves Readers-Writers problem.
 * Read operations acquire read-locks and modify operations acquire
 * write-locks, so that concurrent reads of any kind are possible,
 * but only a single modify operation runs at any given time.
 * This container was implemented in order to get improved
 * performance over CopyOnWriteArrayList. The flaw of
 * CopyOnWriteArrayList is that copy operations are inefficient
 * if writes are not marginally rarer than the reads. This implementation
 * avoids copy operations when modifying the underlying list by
 * making write operations exclusive and reads concurrent. This theoretically
 * reduces concurrency, but it is much more efficient in terms of memory
 * when number of elements in the list is large.
 * Copy operations are still required in order to traverse the list
 * "atomically". Because of this, all iterators over this container
 * behave similarly to CopyOnWriteArrayList, by duplicating the content
 * of the underlying container and returning the iterator over it.
 */
public class ReadWriteList<E> implements List<E> {

    private final List<E> list;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private ReadWriteList(LinkedList<E> l) {
        list = l;
    }

    public ReadWriteList() {
        this(new LinkedList<>());
    }

    @Override
    public int size() {
        readLock.lock();
        try {
            return list.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        readLock.lock();
        try {
            return list.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean contains(Object o) {
        readLock.lock();
        try {
            return list.contains(o);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Iterator<E> iterator() {
        readLock.lock();
        try {
            return new ArrayList<>(list).iterator();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        readLock.lock();
        try {
            List.super.forEach(action);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Object[] toArray() {
        readLock.lock();
        try {
            return list.toArray();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        readLock.lock();
        try {
            return list.toArray(a);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <T> T[] toArray(IntFunction<T[]> generator) {
        readLock.lock();
        try {
            return list.toArray(generator);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean add(E e) {
        writeLock.lock();
        try {
            return list.add(e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        writeLock.lock();
        try {
            return list.remove(o);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        readLock.lock();
        try {
            return new HashSet<>(list).containsAll(c);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        writeLock.lock();
        try {
            return list.addAll(c);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        writeLock.lock();
        try {
            return list.addAll(index, c);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        writeLock.lock();
        try {
            return list.removeAll(c);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        writeLock.lock();
        try {
            return List.super.removeIf(filter);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        writeLock.lock();
        try {
            return list.retainAll(c);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        writeLock.lock();
        try{
            list.replaceAll(operator);
        }finally {
            writeLock.unlock();
        }
    }

    @Override
    public void sort(Comparator<? super E> c) {
        writeLock.lock();
        try{
            List.super.sort(c);
        }finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            list.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public E get(int index) {
        readLock.lock();
        try {
            return list.get(index);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public E set(int index, E element) {
        writeLock.lock();
        try {
            return list.set(index, element);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void add(int index, E element) {
        writeLock.lock();
        try {
            list.add(index, element);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public E remove(int index) {
        writeLock.lock();
        try {
            return list.remove(index);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int indexOf(Object o) {
        readLock.lock();
        try {
            return list.indexOf(o);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int lastIndexOf(Object o) {
        readLock.lock();
        try {
            return list.lastIndexOf(o);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public ListIterator<E> listIterator() {
        readLock.lock();
        try {
            return new ArrayList<>(list).listIterator();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        readLock.lock();
        try {
            return new ArrayList<>(list).listIterator(index);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        readLock.lock();
        try {
            return new ArrayList<>(list).subList(fromIndex, toIndex);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Spliterator<E> spliterator() {
        readLock.lock();
        try{
            return new ArrayList<>(list).spliterator();
        }finally {
            readLock.unlock();
        }
    }

    @Override
    public Stream<E> stream() {
        return List.super.stream();
    }

    @Override
    public Stream<E> parallelStream() {
        return List.super.parallelStream();
    }
}
