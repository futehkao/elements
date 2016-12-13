import net.e6tech.elements.common.util.AsciiArt

welcome = { ->
    def hostName;
    try {
        hostName = java.net.InetAddress.getLocalHost().getHostName();
    } catch (java.net.UnknownHostException ignore) {
        hostName = "localhost";
    }

    def msg = AsciiArt.generate("E6tech", 16)
    return """\
${msg}
Welcome to $hostName !
It is now ${new Date()}
""";
}

prompt = { ->
    return " %"
}
