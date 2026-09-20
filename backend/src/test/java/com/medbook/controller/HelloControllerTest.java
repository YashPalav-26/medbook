package com.medbook.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HelloControllerTest {

    @Autowired
    private HelloController helloController;

    @Test
    void controllerExists() {
        assertThat(helloController).isNotNull();
    }
}