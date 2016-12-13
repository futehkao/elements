import org.crsh.cli.Command
import org.crsh.cli.Option
import org.crsh.cli.Usage

class date {
    @Usage("show the current time")
    @Command
    void main(@Usage("the time format")
                @Option(names=["f","format"])
                        String format) {
        if (format == null) format = "EEE MMM d HH:mm:ss z yyyy";
        def now = new Date();
        // context.attributes.beans['message']  // attributes are global!!!

        if (d == null) {
            d = now;
        }

        /* this is the same as above
        if (context.session['d'] == null) { // session is not persistent across connections"!!!
            context.session['d'] = now
        }*/

        out << d.format(format)
    }
}
