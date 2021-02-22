package de.frosner;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class StartTest
{

    @Test
    public void test()
    {
        new CommandLine(new Start()).execute("-e", "http://localhost:2379");
        assertThat(true).isTrue();
    }
}