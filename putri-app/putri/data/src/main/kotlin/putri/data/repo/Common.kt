package putri.data.repo

typealias Clock = () -> Long

internal val systemClock: Clock = { System.currentTimeMillis() }
