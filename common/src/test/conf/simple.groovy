

exec "$__dir/plugins/plugin.groovy"
exec "$__dir/plugin_test.groovy"

atom("simple") {
    _simple = Object.class
}