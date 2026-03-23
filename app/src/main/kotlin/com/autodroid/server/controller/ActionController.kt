package com.autodroid.server.controller

import com.autodroid.adapter.AutomatorAdapter
import com.autodroid.server.HttpServer

fun registerActionRoutes(server: HttpServer, automator: AutomatorAdapter) {

    // POST /api/actions/click  { "x": 100, "y": 200 }
    server.post("/api/actions/click") { req, res ->
        val body = req.jsonBody
        val x = body.getInt("x")
        val y = body.getInt("y")
        val result = automator.clickPoint(x, y)
        res.sendJson(mapOf("clicked" to result))
    }

    // POST /api/actions/longClick  { "x": 100, "y": 200 }
    server.post("/api/actions/longClick") { req, res ->
        val body = req.jsonBody
        val x = body.getInt("x")
        val y = body.getInt("y")
        val result = automator.longClickPoint(x, y)
        res.sendJson(mapOf("clicked" to result))
    }

    // POST /api/actions/swipe  { "x1":0, "y1":0, "x2":100, "y2":100, "duration":300 }
    server.post("/api/actions/swipe") { req, res ->
        val body = req.jsonBody
        val x1 = body.getInt("x1")
        val y1 = body.getInt("y1")
        val x2 = body.getInt("x2")
        val y2 = body.getInt("y2")
        val duration = body.optLong("duration", 300L).coerceIn(1L, 30_000L)
        val result = automator.swipe(x1, y1, x2, y2, duration)
        res.sendJson(mapOf("swiped" to result))
    }

    // POST /api/actions/key  { "action": "back" | "home" | "recents" | "notifications" }
    server.post("/api/actions/key") { req, res ->
        val body = req.jsonBody
        val action = body.getString("action")
        val result = when (action.lowercase()) {
            "back" -> automator.back()
            "home" -> automator.home()
            "recents" -> automator.recents()
            "notifications" -> automator.notifications()
            else -> throw IllegalArgumentException("Unknown key action: $action")
        }
        res.sendJson(mapOf("performed" to result))
    }

    // POST /api/actions/gesture  { "delay":0, "duration":300, "points":[[x1,y1],[x2,y2],...] }
    server.post("/api/actions/gesture") { req, res ->
        val body = req.jsonBody
        val delay = body.optLong("delay", 0L).coerceIn(0L, 10_000L)
        val duration = body.getLong("duration").coerceIn(1L, 30_000L)
        val pointsArr = body.getJSONArray("points")
        val points = List(pointsArr.length()) { i ->
            val p = pointsArr.getJSONArray(i)
            intArrayOf(p.getInt(0), p.getInt(1))
        }
        val result = automator.gesture(delay, duration, points)
        res.sendJson(mapOf("performed" to result))
    }
}
