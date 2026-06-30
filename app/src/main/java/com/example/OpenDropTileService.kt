package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class OpenDropTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        val currentDiscoverable = prefs.getBoolean("discoverable", true)
        val newDiscoverable = !currentDiscoverable

        // Update preferences
        prefs.edit().putBoolean("discoverable", newDiscoverable).apply()

        // Start/Stop service
        val serviceIntent = Intent(this, OpenDropService::class.java)
        if (newDiscoverable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("opendrop_prefs", Context.MODE_PRIVATE)
        val discoverable = prefs.getBoolean("discoverable", true)

        if (discoverable) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "OpenDrop"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "On"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "OpenDrop"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Off"
            }
        }
        tile.updateTile()
    }
}
