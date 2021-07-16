package com.example.maps.models.direction

interface DirectionFinderListener {
    fun onDirectionFinderStart()
    fun onDirectionFinderSuccess(route: List<Route>)
}