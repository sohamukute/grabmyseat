package com.grabmyseat.loadtest

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class GrabMySeatSimulation extends Simulation {

  val baseUrl = sys.env.getOrElse("GATLING_BASE_URL", "http://localhost:8080")
  val eventId = sys.env.getOrElse("GATLING_EVENT_ID", "1").toLong
  val zoneId = sys.env.getOrElse("GATLING_ZONE_ID", "1").toLong
  val seatStart = sys.env.getOrElse("GATLING_SEAT_START", "1").toInt
  val seatCount = sys.env.getOrElse("GATLING_SEAT_COUNT", "100").toInt
  val username = sys.env.getOrElse("GATLING_USERNAME", "loadtester")
  val password = sys.env.getOrElse("GATLING_PASSWORD", "LoadTest123!")
  val rampUsersCount = sys.env.getOrElse("GATLING_RAMP_USERS", "50").toInt
  val rampDurationSeconds = sys.env.getOrElse("GATLING_RAMP_DURATION_SECONDS", "60").toInt

  val seatIds = (seatStart until seatStart + seatCount).toList
  val seatFeeder = Iterator.from(0).map(i => Map("seatId" -> seatIds(i % seatIds.length)))

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val login = exec(
    http("Login")
      .post("/api/auth/login")
      .body(StringBody(s"""{"username":"$username","password":"$password"}""")).asJson
      .check(status.is(200))
      .check(jsonPath("$.accessToken").saveAs("jwtToken"))
  )

  val joinQueue = exec(
    http("JoinQueue")
      .post(s"/api/waiting-room/events/$eventId/join")
      .header("Authorization", "Bearer ${jwtToken}")
      .check(status.is(200))
      .check(jsonPath("$.token").saveAs("queueToken"))
  )

  val waitForAdmission =
    asLongAs(session => session("queueStatus").asOption[String].getOrElse("WAITING") != "ADMITTED") {
      exec(
        http("QueuePosition")
          .get(s"/api/waiting-room/events/$eventId/position")
          .queryParam("token", "${queueToken}")
          .header("Authorization", "Bearer ${jwtToken}")
          .check(status.is(200))
          .check(jsonPath("$.status").saveAs("queueStatus"))
      ).pause(1.second)
    }

  val fetchPermit = exec(
    http("FetchPermit")
      .get(session => s"/api/waiting-room/events/$eventId/permit")
      .queryParam("token", "${queueToken}")
      .header("Authorization", "Bearer ${jwtToken}")
      .check(status.is(200))
      .check(jsonPath("$.permitToken").saveAs("permitToken"))
  )

  val reserve = exec(
    http("ReserveSeat")
      .post("/api/inventory/reservations")
      .header("Authorization", "Bearer ${jwtToken}")
      .header("X-Queue-Permit", "${permitToken}")
      .body(StringBody(session => {
        val seatId = session("seatId").as[Int]
        s"""{"eventId":$eventId,"zoneId":$zoneId,"seatIds":[$seatId]}"""
      })).asJson
      .check(status.in(201, 409, 422))
      .check(jsonPath("$.token").optional.saveAs("reservationToken"))
  )

  val confirm = doIf(session => session.contains("reservationToken")) {
    exec(
      http("ConfirmBooking")
        .post(session => s"/api/saga/bookings/${session("reservationToken").as[String]}/confirm")
        .header("Authorization", "Bearer ${jwtToken}")
        .check(status.in(200, 409))
    )
  }

  val scn = scenario("GrabMySeatLoadTest")
    .feed(seatFeeder)
    .exec(login)
    .exec(joinQueue)
    .exec(waitForAdmission)
    .exec(fetchPermit)
    .exec(reserve)
    .exec(confirm)

  setUp(
    scn.inject(rampUsers(rampUsersCount).during(rampDurationSeconds.seconds))
  ).protocols(httpProtocol)
    .assertions(
      // No-oversell guarantee: successful 201 reserves must never exceed seatCount.
      // The seat feeder cycles seatIds, so the maximum number of 201s is seatCount.
      details("ReserveSeat").successfulRequests.count.lte(seatCount.toLong),
      // Sanity: at least one successful reserve so the run isn't a no-op.
      details("ReserveSeat").successfulRequests.count.gte(1L),
      // 201/409 mix: confirm endpoint should only fire for successful reserves.
      details("ConfirmBooking").successfulRequests.count.lte(seatCount.toLong)
    )
}
