pluginDirectories = ["$__DIR/plugins/**"]
resourceManager.plugin.loadPlugins(pluginDirectories as String[])

Class cls = getClass().classLoader.loadClass("net.e6tech.elements.common.resources.plugin.TestPlugin")

component("simple") {
    _simple = Object.class
}