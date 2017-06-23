import net.e6tech.elements.common.util.AsciiArt

exec "$__dir/plugins/plugin.groovy"
exec "$__dir/plugin_test.groovy"

atom("simple") {
    _simple = AsciiArt
}