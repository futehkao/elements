pluginDirectories = ["$__dir/plugins/**"]
resourceManager.plugin.loadPlugins(pluginDirectories as String[])

Class cls = getClass().classLoader.loadClass("net.e6tech.elements.common.resources.plugin.TestPlugin")

atom("simple") {
    _simple = Object.class
}