package com.aizistral.fetchdeploy.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.experimental.Helper;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Lists {

    @SafeVarargs
    public <T> List<T> create(T... elements) {
        List<T> list = new ArrayList<>();
        Arrays.stream(elements).forEach(list::add);
        return list;
    }

}
