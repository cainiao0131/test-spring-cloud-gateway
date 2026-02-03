package com.cainiao.gateway.util;

import lombok.experimental.UtilityClass;

import java.util.Date;

@UtilityClass
public class Util {

    public static void print(Object message) {
        System.out.printf("%s | %s | %s%n", Thread.currentThread().getName(), new Date(), message);
    }
}
