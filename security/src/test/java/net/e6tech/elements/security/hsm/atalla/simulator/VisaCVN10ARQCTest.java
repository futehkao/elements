package net.e6tech.elements.security.hsm.atalla.simulator;

import net.e6tech.elements.security.Hex;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VisaCVN10ARQCTest {

    @Test
    public void testAtallaSample() throws GeneralSecurityException, CommandException {
        String dataBlock = "0123456789ABCDEF0123456789ABCDEF";
        while (Hex.toBytes(dataBlock).length % 8 != 0) {
            dataBlock += "00";
        }

        // String arqc = "EA53002B4A6AF97A"; // 9F26
        String arqc = "6513D2FC43696694";
        String arc  = "0000";
        AtallaSimulator simulator = new AtallaSimulator();
        VisaCVN10ARQC mc = new VisaCVN10ARQC(simulator);
        AKB akb = simulator.asAKB("1mENE000", Hex.toBytes("0123456789ABCDEFFEDCBA9876543210"));
        assertEquals("8AB6708833051A7258342A74919328D5BCAADF60322ABF10", akb.getEncryptedKey());
        assertEquals("A05A674C696D25AA", akb.getMac());
        String result = mc.imk(akb)
                .pan("9990123456789012")
                .cardSequence("45")
                .arqc(arqc)
                .dataBlock(dataBlock)
                .arc(arc)
                .process();
        System.out.println("command result: " + result);
        System.out.println("arqc " + arqc + ", is equals to computedARQC: " + arqc.equals(mc.getComputedARQC()));
        System.out.println("ARPC: " + mc.computeARPC(arc));
        assertEquals("03FC005180103F23", mc.computeARPC(arc));
        String[] resultFields = result.split("#");
        assertEquals("450", resultFields[0]);
        assertEquals("AADB", resultFields[2]);
        assertEquals("08D7", resultFields[3]);
    }

    @Test
    public void testDataGeneratedBySimulator() throws GeneralSecurityException, CommandException {
        // data block
        String amount          = "000000012300"; // 9F02
        String otherAmount     = "000000000000"; // 9F03
        String terminalCountry = "0840";         // 9F1A
        String terminalResult  = "8000010000";   // 95, TVR, Terminal Verification Result
        String txCurrency      = "0840";         // 5F2A
        String txDate          = "191223";       // 9A
        String txType          = "00";           // 9C
        String unpredictable   = "9BADBCAB";     // 9F37
        String aip             = "0000";         // 82, AIP, Application Interchange Profile
        String atc             = "00FF";         // 9F36, ATC, Application Transaction Counter
        String cvr             = "03A00000";     // From 9F10 (right 8 of 06010A03A00000), CVR, Card Verification Result, from Issuer Application Data (9F10), VIS: 4 bytes (8 in HEX)
        // for CVN = 10, padding with 00 (not 80)
        String dataBlock = amount + otherAmount + terminalCountry + terminalResult + txCurrency + txDate + txType + unpredictable + aip + atc + cvr;
        dataBlock += "00";
        while (Hex.toBytes(dataBlock).length % 8 != 0) {
            dataBlock += "00";
        }

        String arqc = "4B2CCA57D04822B1"; // 9F26
        String arc  = "0000";
        AtallaSimulator simulator = new AtallaSimulator();
        VisaCVN10ARQC mc = new VisaCVN10ARQC(simulator);
        String result = mc.imk(simulator.asAKB(simulator.IMK_ARQC))
                .pan("5555550000000002")
                .cardSequence("01")
                .arqc(arqc)
                .dataBlock(dataBlock)
                .arc(arc)
                .process();
        System.out.println("command result: " + result);
        System.out.println("arqc " + arqc + ", is equals to computedARQC: " + arqc.equals(mc.getComputedARQC()));
        System.out.println("ARPC: " + mc.computeARPC(arc)); // 388307867C2777C3
        assertEquals(arqc, mc.getComputedARQC());
        assertEquals("388307867C2777C3", mc.computeARPC(arc));
        String[] resultFields = result.split("#");
        assertEquals("450", resultFields[0]);
        assertEquals("989E", resultFields[2]);
        assertEquals("08D7", resultFields[3]);
    }

}
