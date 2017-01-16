package com.github.opaluchlukasz.junit2spock.core

import spock.lang.Specification

import static java.nio.charset.StandardCharsets.UTF_8

class SpockerTest extends Specification {

    def 'should return groovy class for java source'() {
        given:
        String source = readFromResources('MyTest.java')
        String expected = normalize(readFromResources('MyTest.groovy'))
        Spocker spocker = new Spocker(source)

        expect:
        normalize(spocker.asGroovyClass()) == expected
    }

    private String readFromResources(String filename) {
        new Scanner(getClass().getClassLoader().getResourceAsStream(filename), UTF_8.toString()).useDelimiter("\\A").next()
    }

    private static String normalize(String s) {
        s.replaceAll('\r', '')
    }
}
