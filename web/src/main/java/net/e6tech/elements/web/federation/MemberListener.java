package net.e6tech.elements.web.federation;

public interface MemberListener {
    void added(HailingFrequency frequency);
    void removed(HailingFrequency frequency);
}
