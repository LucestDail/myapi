package com.example.myapi.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyapiSecurityPropertiesTest {

    @Test
    void 기본은_인증비활성_fail_open() {
        var p = new MyapiSecurityProperties();
        assertFalse(p.isAuthEnabled());
        assertFalse(p.isValidKey("anything")); // 비활성 시 어떤 키도 유효하지 않음(하지만 필터가 통과시킴)
    }

    @Test
    void null_또는_공백키는_비활성() {
        var p = new MyapiSecurityProperties();
        p.setApiKey(null);
        assertFalse(p.isAuthEnabled());
        p.setApiKey("   ");
        assertFalse(p.isAuthEnabled());
    }

    @Test
    void 키설정시_일치검증() {
        var p = new MyapiSecurityProperties();
        p.setApiKey("SECRET");
        assertTrue(p.isAuthEnabled());
        assertTrue(p.isValidKey("SECRET"));
        assertFalse(p.isValidKey("secret"));   // 대소문자 구분
        assertFalse(p.isValidKey("SECRE"));     // 길이 다름
        assertFalse(p.isValidKey(null));
    }
}
