package com.martmists.hackgame.client

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.dialogs.MessageDialog
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton
import com.martmists.hackgame.client.ui.Screen
import com.martmists.hackgame.common.packets.PingPacket
import com.martmists.hackgame.common.registry.BuiltinPackets
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.system.exitProcess

object ClientPacketCallbacks {
    init {
        BuiltinPackets.DISCONNECT_S2C.handler { packet, context ->
            context.connection.close()

            if (packet.reason == "exit") {
                exitProcess(0)
            }

            Client.INSTANCE.LOGGER.warn("Disconnected from server! Reason: ${packet.reason}")
            MessageDialog.showMessageDialog(Screen.gui, "Disconnected", "Server told us to disconnect: ${packet.reason}", MessageDialogButton.OK)
            if (packet.reconnect) {
                Client.INSTANCE.reconnect(context.connection.socket.inetAddress.hostName, context.connection.socket.port)
            } else {
                exitProcess(0)
            }
        }

        BuiltinPackets.PING_S2C.handler { packet, context ->
            // TODO: Check last ping value
            thread(start = true, isDaemon=true, name = "PingTimeout") {
                Thread.sleep(60_000)
                BuiltinPackets.PING_C2S.send(PingPacket(packet.current, Random.nextInt()), context.connection)
            }
        }

        BuiltinPackets.FEEDBACK_S2C.handler { packet, context ->
            Screen.logText.addText(packet.msg)
        }

        BuiltinPackets.HOST_CONNECT_S2C.handler { packet, context ->
            Screen.infoText.clearText()
            Screen.infoText.addText("Connected to: ${if (Client.INSTANCE.hideIPs) "[REDACTED]" else packet.host}")
        }

        BuiltinPackets.HOST_DISCONNECT_S2C.handler { packet, context ->
            Screen.infoText.clearText()
            Screen.infoText.addText("Connected to: ${if (Client.INSTANCE.hideIPs) "[REDACTED]" else packet.current}")
        }
    }

    fun initialize() {
        Client.INSTANCE.LOGGER.info("Registered client packet callbacks")
    }
}