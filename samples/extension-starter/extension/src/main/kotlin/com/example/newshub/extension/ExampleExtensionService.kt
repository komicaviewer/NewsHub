package com.example.newshub.extension

import tw.kevinzhang.extension_api.IsolatedSourceService

class ExampleExtensionService : IsolatedSourceService() {
    override val source = ExampleSource()
}
