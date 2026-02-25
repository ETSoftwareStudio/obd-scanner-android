package com.eltonvs.obdapp.data.connection

import com.eltonvs.obdapp.domain.model.DeviceInfo

interface ObdTransport {
    suspend fun connect(device: DeviceInfo): Result<Unit>
    suspend fun disconnect()
    fun isConnected(): Boolean
    fun getInputStream(): java.io.InputStream?
    fun getOutputStream(): java.io.OutputStream?
}
