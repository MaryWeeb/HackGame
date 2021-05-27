package com.martmists.hackgame.server.game

import com.martmists.hackgame.common.DisconnectException
import com.martmists.hackgame.common.packets.*
import com.martmists.hackgame.common.registry.BuiltinPackets
import com.martmists.hackgame.server.Server
import com.martmists.hackgame.server.database.DatabaseManager
import com.martmists.hackgame.server.database.dataholders.StoredAccount
import com.martmists.hackgame.server.database.dataholders.StoredHostDevice
import com.martmists.hackgame.server.database.dataholders.vfs.VFSDirectory
import com.martmists.hackgame.server.database.dataholders.vfs.VFSFile
import com.martmists.hackgame.server.database.tables.AccountTable
import com.martmists.hackgame.server.database.tables.HostTable
import com.martmists.hackgame.server.entities.ServerCommandSource
import com.martmists.hackgame.server.entities.ServerConnection
import com.toxicbakery.bcrypt.Bcrypt
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import java.util.*

class PlayerSession(val connection: ServerConnection) {
    var isLoggedIn = false
    val connectChain = Stack<HostDevice>()
    var currentIP = ""
    var account = StoredAccount(" ", "")

    fun onLoginPacket(packet: LoginPacket) {
        if (Server.INSTANCE.connected.any { it.session.account.name == packet.name }) {
            // Already logged in
            BuiltinPackets.DISCONNECT_S2C.send(DisconnectPacket("User already logged in on different connection", true), connection)
            throw DisconnectException()
        }

        if (packet.register) {
            val address = HostManager.getRandomAvailableIp()

            val foundAccounts = DatabaseManager.transaction {
                AccountTable.select { AccountTable.username eq packet.name }.map { it[AccountTable.username] }
            }.get()

            if (foundAccounts.isNotEmpty()) {
                BuiltinPackets.DISCONNECT_S2C.send(DisconnectPacket("Invalid login.", true), connection)
                throw DisconnectException()
            }

            HostManager.createStoredHost(address, StoredHostDevice(0, listOf(), VFSDirectory.empty()))

            DatabaseManager.transaction {
                AccountTable.insert {
                    it[AccountTable.username] = packet.name
                    it[AccountTable.passwordHash] = Bcrypt.hash(packet.password, 10)
                    it[AccountTable.homeAddress] = address
                }
            }

            account = StoredAccount(packet.name, address)
        } else {
            val foundAccount = DatabaseManager.transaction {
                return@transaction AccountTable.select { AccountTable.username eq packet.name }.firstOrNull { Bcrypt.verify(packet.password, it[AccountTable.passwordHash]) }?.let { StoredAccount(packet.name, it[AccountTable.homeAddress]) }
            }.get()

            if (foundAccount == null) {
                BuiltinPackets.DISCONNECT_S2C.send(DisconnectPacket("Invalid login.", true), connection)
                throw DisconnectException()
            }

            account = foundAccount
        }

        val host = HostManager.loadOrCreateStoredHost(account.homeIP, StoredHostDevice(0, listOf(), VFSDirectory.empty()))
        connectChain.push(host)
        BuiltinPackets.HOST_CONNECT_S2C.send(HostConnectPacket(account.homeIP), connection)
        currentIP = account.homeIP
        isLoggedIn = true
    }

    fun onCommandPacket(packet: CommandPacket) {
        val source = ServerCommandSource(connection)
        val parsed = Server.INSTANCE.dispatcher.parse(packet.cmd, source)
        Server.INSTANCE.dispatcher.execute(parsed)
    }

    fun connectTo(remoteIp: String) {
        // TODO: Check if IP allows login or something idk
        if (HostManager.activeHosts.containsKey(remoteIp)) {
            if (connectChain.firstElement().ip == remoteIp) {
                BuiltinPackets.FEEDBACK_S2C.send(FeedbackPacket("ERROR: Cannot connect to root host"), connection)
                return
            }
            currentIP = remoteIp
            connectChain.push(HostManager.activeHosts[remoteIp]!!)
            HostManager.activeHosts[remoteIp]!!.logConnection(currentIP)
            BuiltinPackets.HOST_CONNECT_S2C.send(HostConnectPacket(remoteIp), connection)
        } else {
            BuiltinPackets.FEEDBACK_S2C.send(FeedbackPacket("ERROR: No such host: $remoteIp"), connection)
        }
    }
}