package net.e6tech.elements.security.hsm.atalla.simulator;

import net.e6tech.elements.security.Hex;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VisaCVN18ARQCTest {

    @Test
    public void testAtallaSample() throws GeneralSecurityException, CommandException {
        String dataBlock = "0123456789ABCDEF0123456789ABCDEF";
        while (Hex.toBytes(dataBlock).length % 8 != 0) {
            dataBlock += "00";
        }

        // String arqc = "EA53002B4A6AF97A"; // 9F26
        String arqc = "6513D2FC43696694";
        String arc  = "00008000"; // 4 bytes card status updates
        AtallaSimulator simulator = new AtallaSimulator();
        VisaCVN18ARQC mc = new VisaCVN18ARQC(simulator);
        AKB akb = simulator.asAKB("1mENE000", Hex.toBytes("0123456789ABCDEFFEDCBA9876543210"));
        assertEquals("8AB6708833051A7258342A74919328D5BCAADF60322ABF10", akb.getEncryptedKey());
        assertEquals("A05A674C696D25AA", akb.getMac());
        String result = mc.imk(akb)
                .pan("9990123456789012")
                .cardSequence("45")
                .diversification("1234567890123456")
                .arqc(arqc)
                .dataBlock(dataBlock)
                .arc(arc)
                .process();
        System.out.println("command result: " + result);
        System.out.println("arqc " + arqc + ", is equals to computedARQC: " + arqc.equals(mc.getComputedARQC()));
        System.out.println("ARPC: " + mc.computeARPC(arc));
        assertEquals("44A67041", mc.computeARPC(arc));
        String[] resultFields = result.split("#");
        assertEquals("450", resultFields[0]);
        assertEquals("A4EC", resultFields[2]);
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
        String txDate          = "200106";       // 9A
        String txType          = "00";           // 9C
        String unpredictable   = "9BADBCAB";     // 9F37
        String aip             = "0000";         // 82, AIP, Application Interchange Profile
        String atc             = "0102";         // 9F36, ATC, Application Transaction Counter
        String iad             = "06011203A00000";     // 9F10, not CVR but IAD
        // for CVN = 18, padding with 80
        String dataBlock = amount + otherAmount + terminalCountry + terminalResult + txCurrency + txDate + txType + unpredictable + aip + atc + iad;
        dataBlock += "80";
        while (Hex.toBytes(dataBlock).length % 8 != 0) {
            dataBlock += "00";
        }

        String arqc = "9DA57063E9BF4757"; // 9F26
        String arc  = "00008000"; // 4 bytes card status updates
        AtallaSimulator simulator = new AtallaSimulator();
        VisaCVN18ARQC mc = new VisaCVN18ARQC(simulator);
        String result = mc.imk(simulator.asAKB(simulator.IMK_ARQC))
                .pan("5555550000000002")
                .cardSequence("01")
                .diversification(atc + "000000000000")
                .arqc(arqc)
                .dataBlock(dataBlock)
                .arc(arc)
                .process();
        System.out.println("command result: " + result);
        System.out.println("arqc " + arqc + ", is equals to computedARQC: " + arqc.equals(mc.getComputedARQC()));
        System.out.println("ARPC: " + mc.computeARPC(arc));
        assertEquals(arqc, mc.getComputedARQC());
        assertEquals("C5861E6D", mc.computeARPC(arc));
        String[] resultFields = result.split("#");
        assertEquals("450", resultFields[0]);
        assertEquals("1A26", resultFields[2]);
        assertEquals("08D7", resultFields[3]);
    }

}
