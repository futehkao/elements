package net.e6tech.elements.security.hsm.atalla.simulator;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MasterCardARQCTest {

    @Test
    public void testAtallaSample() throws GeneralSecurityException, CommandException {
        AtallaSimulator simulator = new AtallaSimulator();
        MasterCardARQC mc = new MasterCardARQC(simulator);
        String arqc = "922F3E83125EB46B"; // arqc is not the same as the sample
        String arc = "0000";
        String result = mc.imk(simulator.asAKB(simulator.IMK_ARQC))
                .pan("9901234567890123")
                .cardSequence("45")
                .diversification("1234567890123456")
                .arqc(arqc)
                .dataBlock("0123456789ABCDEF0123456789ABCDEF")
                .arc(arc)
                .process();

        System.out.println("command result: " + result);
        System.out.println("arqc " + arqc + ", is equals to computedARQC: " + arqc.equals(mc.getComputedARQC()));
        System.out.println("ARPC: " + mc.computeARPC(arc));
        assertEquals("8AE6E836084B0E80", mc.computeARPC(arc));
        String[] resultFields = result.split("#");
        assertEquals("450", resultFields[0]);
        assertEquals("0995", resultFields[2]);
        assertEquals("08D7", resultFields[3]);
    }

}
