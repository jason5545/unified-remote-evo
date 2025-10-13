package com.unifiedremote.evo.network

import com.unifiedremote.evo.data.*
import java.io.*

/**
 * Packet 序列化器
 */
object PacketSerializer {

    fun serialize(packet: Packet): ByteArray {
        val baos = ByteArrayOutputStream()
        val writer = BinaryWriter(DataOutputStream(baos))

        writer.writeType(BinaryWriter.TYPE_ROOT_DICT)

        packet.action?.let { writer.writeField("Action", it) }
        packet.request?.let { writer.writeField("Request", it) }
        packet.response?.let { writer.writeField("Response", it) }
        packet.keepAlive?.let { writer.writeField("KeepAlive", it) }
        packet.session?.let { writer.writeField("Session", it) }
        packet.source?.let { writer.writeField("Source", it) }
        packet.destination?.let { writer.writeField("Destination", it) }
        packet.version?.let { writer.writeField("Version", it) }
        packet.password?.let { writer.writeField("Password", it) }
        packet.platform?.let { writer.writeField("Platform", it) }
        packet.security?.let { writer.writeField("Security", it) }
        packet.id?.let { writer.writeField("ID", it) }
        packet.hash?.let { writer.writeField("Hash", it) }

        packet.capabilities?.let { cap ->
            writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
            writer.writeString("Capabilities")
            serializeCapabilities(writer, cap)
            writer.writeEnd()
        }

        packet.run?.let { action ->
            writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
            writer.writeString("Run")
            serializeAction(writer, action)
        }

        packet.layout?.let { layout ->
            writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
            writer.writeString("Layout")
            serializeLayout(writer, layout)
        }

        writer.writeEnd()
        return baos.toByteArray()
    }

    private fun serializeAction(writer: BinaryWriter, action: Action) {
        writer.writeField("Name", action.name)
        action.target?.let { writer.writeField("Target", it) }

        action.extras?.let { extras ->
            writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
            writer.writeString("Extras")
            serializeExtras(writer, extras)
            writer.writeEnd()  // 結束 Extras dictionary
        }

        writer.writeEnd()  // 結束 Action dictionary
    }

    private fun serializeExtras(writer: BinaryWriter, extras: Extras) {
        writer.writeType(BinaryWriter.TYPE_ARRAY)
        writer.writeString("Values")

        for (extra in extras.values) {
            // 陣列項目：TYPE_NAMED_DICT + null name + 欄位 + END
            writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
            writer.writeString(null)  // 陣列項目沒有名稱
            writer.writeField("Key", extra.key)
            writer.writeField("Value", extra.value)
            writer.writeEnd()  // 結束此 Extra 項目
        }

        writer.writeEnd()  // 結束 Values 陣列
    }

    private fun serializeLayout(writer: BinaryWriter, layout: Layout) {
        layout.id?.let { writer.writeField("ID", it) }
        layout.hash?.let { writer.writeField("Hash", it) }

        layout.controls?.let { controls ->
            writer.writeType(BinaryWriter.TYPE_ARRAY)
            writer.writeString("Controls")

            for (control in controls) {
                writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
                writer.writeString(null)  // 陣列項目沒有名稱
                serializeControl(writer, control)
            }

            writer.writeEnd()  // 結束 Controls 陣列
        }

        writer.writeEnd()  // 結束 Layout dictionary
    }

    private fun serializeControl(writer: BinaryWriter, control: Control) {
        control.type?.let { writer.writeField("Type", it) }

        control.onAction?.let { action ->
            writer.writeType(BinaryWriter.TYPE_NAMED_DICT)
            writer.writeString("OnAction")
            serializeAction(writer, action)
        }

        writer.writeEnd()  // 結束 Control dictionary
    }

    private fun serializeCapabilities(writer: BinaryWriter, cap: Capabilities) {
        // 原始 APP 只序列化非 null 欄位
        cap.fast?.let {
            writer.writeField("Fast", it)
            println("DEBUG: 序列化 Fast = $it")
        }
        cap.clientNonce?.let { writer.writeField("ClientNonce", it) }
        cap.encryption2?.let { writer.writeField("Encryption2", it) }
        cap.sync?.let { writer.writeField("Sync", it) }
        cap.actions?.let { writer.writeField("Actions", it) }
        cap.grid?.let { writer.writeField("Grid", it) }
        cap.loading?.let { writer.writeField("Loading", it) }
        cap.business?.let { writer.writeField("Business", it) }
    }

    fun deserialize(data: ByteArray): Packet? {
        return try {
            val reader = BinaryReader(DataInputStream(ByteArrayInputStream(data)))
            val type = reader.readType()
            if (type != BinaryReader.TYPE_ROOT_DICT) null
            else mapToPacket(reader.readDictionary())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun mapToPacket(map: Map<String, Any?>): Packet {
        val packet = Packet()
        packet.action = (map["Action"] as? Byte)
        packet.request = (map["Request"] as? Byte)
        packet.response = (map["Response"] as? Byte)
        packet.keepAlive = (map["KeepAlive"] as? Boolean)
        packet.session = (map["Session"] as? String)
        packet.source = (map["Source"] as? String)
        packet.destination = (map["Destination"] as? String)
        packet.version = (map["Version"] as? Int)
        packet.password = (map["Password"] as? String)
        packet.platform = (map["Platform"] as? String)
        packet.security = (map["Security"] as? Byte)
        packet.id = (map["ID"] as? String)
        packet.hash = (map["Hash"] as? Int)

        (map["Run"] as? Map<*, *>)?.let { actionMap ->
            @Suppress("UNCHECKED_CAST")
            packet.run = mapToAction(actionMap as Map<String, Any?>)
        }

        (map["Capabilities"] as? Map<*, *>)?.let { capMap ->
            @Suppress("UNCHECKED_CAST")
            packet.capabilities = mapToCapabilities(capMap as Map<String, Any?>)
        }

        return packet
    }

    private fun mapToAction(map: Map<String, Any?>): Action {
        val name = map["Name"] as? String ?: ""
        val target = map["Target"] as? String
        val extras = (map["Extras"] as? Map<*, *>)?.let { extrasMap ->
            @Suppress("UNCHECKED_CAST")
            mapToExtras(extrasMap as Map<String, Any?>)
        }
        return Action(name, target, extras)
    }

    private fun mapToExtras(map: Map<String, Any?>): Extras {
        val extras = Extras()
        val values = map["Values"] as? List<*> ?: return extras

        for (item in values) {
            (item as? Map<*, *>)?.let { extraMap ->
                val key = extraMap["Key"] as? String ?: ""
                val value = extraMap["Value"] as? String ?: ""
                extras.values.add(Extra(key, value))
            }
        }

        return extras
    }

    private fun mapToCapabilities(map: Map<String, Any?>): Capabilities {
        return Capabilities(
            fast = map["Fast"] as? Boolean,
            clientNonce = map["ClientNonce"] as? Boolean,
            encryption2 = map["Encryption2"] as? Boolean,
            sync = map["Sync"] as? Boolean,
            actions = map["Actions"] as? Boolean,
            grid = map["Grid"] as? Boolean,
            loading = map["Loading"] as? Boolean,
            business = map["Business"] as? Boolean
        )
    }
}
