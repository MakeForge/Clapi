package io.github.crustack.clapi.core.storage.opencollection

import io.github.crustack.clapi.core.model.opencollection.CollectionConfig
import io.github.crustack.clapi.core.model.opencollection.CollectionInfo
import io.github.crustack.clapi.core.model.opencollection.CollectionProtobufConfig
import io.github.crustack.clapi.core.model.opencollection.OpenCollectionDocument
import io.github.crustack.clapi.core.model.opencollection.ProtoFile
import io.github.crustack.clapi.core.model.opencollection.ProtoFileImportPath
import io.github.crustack.clapi.core.model.opencollection.ProtoFileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenCollectionProtobufStorageTest {

    private val validator = OpenCollectionSchemaValidator()

    @Test
    fun roundtrip_and_schema_validation_of_collection_protobuf_config() {
        val originalDoc = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "Protobuf Enabled Collection",
                version = "1.0.0",
            ),
            config = CollectionConfig(
                protobuf = CollectionProtobufConfig(
                    protoFiles = listOf(
                        ProtoFile(path = "shared/protos/greeter.proto"),
                        ProtoFile(path = "/absolute/path/to/service.proto"),
                    ),
                    importPaths = listOf(
                        ProtoFileImportPath(path = "shared/protos/common/metadata.proto", disabled = false),
                        ProtoFileImportPath(path = "/external/includes", disabled = true),
                    ),
                )
            ),
        )

        val json = OpenCollectionSerializer.serializeJson(originalDoc)

        val issues = validator.validate(json)
        assertTrue(issues.isEmpty(), "Schema validation failed: $issues")

        val deserialized = OpenCollectionSerializer.deserializeJson(json)

        assertNotNull(deserialized.config?.protobuf)
        val protoConfig = deserialized.config!!.protobuf!!

        assertEquals(2, protoConfig.protoFiles.size)
        assertEquals(ProtoFileType.FILE, protoConfig.protoFiles[0].type)
        assertEquals("shared/protos/greeter.proto", protoConfig.protoFiles[0].path)
        assertEquals("/absolute/path/to/service.proto", protoConfig.protoFiles[1].path)

        assertEquals(2, protoConfig.importPaths.size)
        assertEquals("shared/protos/common/metadata.proto", protoConfig.importPaths[0].path)
        assertEquals(false, protoConfig.importPaths[0].disabled)
        assertEquals("/external/includes", protoConfig.importPaths[1].path)
        assertEquals(true, protoConfig.importPaths[1].disabled)
    }

    @Test
    fun document_without_protobuf_config_remains_valid() {
        val docWithoutProtobuf = OpenCollectionDocument(
            opencollection = "1.0.0",
            info = CollectionInfo(
                name = "Plain Collection",
                version = "1.0.0",
            ),
        )

        val json = OpenCollectionSerializer.serializeJson(docWithoutProtobuf)
        val issues = validator.validate(json)
        assertTrue(issues.isEmpty(), "Schema validation failed: $issues")

        val deserialized = OpenCollectionSerializer.deserializeJson(json)
        assertNull(deserialized.config?.protobuf)
    }
}
