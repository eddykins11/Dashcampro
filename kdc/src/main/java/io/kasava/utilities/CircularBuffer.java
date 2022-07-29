package io.kasava.utilities;

import java.util.ArrayList;

public class CircularBuffer<T> {

    private ArrayList<T> array;

    public CircularBuffer() {
        array = new ArrayList<>();
    }

    public void insert(T element) {
        array.add(0, element);

        while(array.size() > 40) {
            array.remove(array.size()-1);
        }
    }

    public T read(int pos) {
        T element = null;

        if(pos < array.size()) {
            element = array.get(pos);
        }

        return element;
    }

    public int size() {
        return array.size();
    }
}