package com.aizistral.fetchdeploy.misc;

@FunctionalInterface
public interface Handler<T, E extends Exception> {

    public void accept(T value) throws E;

}
