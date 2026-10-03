// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  localInput,
  instant,
  nextSlot,
  clockTime,
  editable,
  actions,
  slotStyle,
} from "./schema.js";
test("server UTC is displayed in fixed Shanghai time", () =>
  assert.equal(localInput("2026-10-05T02:00:00Z"), "2026-10-05T10:00"));
test("local appointment round-trips without browser timezone dependence", () =>
  assert.equal(instant("2026-10-05T10:00"), "2026-10-05T02:00:00.000Z"));
test("impossible date and missing input are rejected", () => {
  assert.throws(() => instant("2026-02-30T10:00"));
  assert.throws(() => instant(""));
});
test("default suggestion advances to next quarter hour", () =>
  assert.equal(nextSlot("2026-10-05T02:00:00Z"), "2026-10-05T02:15:00.000Z"));
test("minimum notice advances suggestion before rounding", () =>
  assert.equal(
    nextSlot("2026-10-05T02:01:00Z", 30),
    "2026-10-05T02:45:00.000Z",
  ));
test("midnight closing renders 24:00", () =>
  assert.equal(clockTime(1440), "24:00"));
test("another owners draft has no edit entry", () =>
  assert.equal(
    editable(
      { ownerId: 2, status: "DRAFT" },
      { id: 1, permissions: ["booking.write"] },
    ),
    false,
  ));
test("reviewer without approval permission sees no approval entry", () =>
  assert.deepEqual(
    actions(
      { ownerId: 2, reviewerId: 1, status: "PENDING" },
      { id: 1, permissions: ["booking.read"] },
      {},
    ),
    [],
  ));
test("pending owner has cancellation but no approval or check-in", () =>
  assert.deepEqual(
    actions(
      { ownerId: 1, reviewerId: 2, status: "PENDING" },
      { id: 1, permissions: ["booking.read", "booking.write"] },
      {},
    ),
    ["cancel"],
  ));
test("cross-day occupancy is clipped to the current day", () =>
  assert.deepEqual(
    slotStyle(
      { startsAt: "2026-10-04T15:30:00Z", endsAt: "2026-10-05T00:00:00Z" },
      "2026-10-05",
    ),
    { left: "0%", width: (480 / 1440) * 100 + "%" },
  ));
