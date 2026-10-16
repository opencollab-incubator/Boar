package ac.boar.anticheat.packets.input;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.data.block.BoarBlockState;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import ac.boar.anticheat.ack.types.DimensionSwitchAck;
import ac.boar.anticheat.ack.types.RespawnStateAck;
import ac.boar.anticheat.check.impl.reach.Reach;
import ac.boar.anticheat.check.impl.badpackets.BadPacketA;
import ac.boar.anticheat.check.impl.timer.Timer;
import ac.boar.anticheat.packets.input.legacy.LegacyAuthInputPackets;
import ac.boar.anticheat.packets.input.teleport.TeleportHandler;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.BoatTopRetry;
import ac.boar.anticheat.prediction.CollisionRetry;
import ac.boar.anticheat.prediction.PredictionRunner;
import ac.boar.anticheat.prediction.PredictionState;
import ac.boar.anticheat.prediction.vehicle.VehiclePredictionRunner;
import ac.boar.anticheat.prediction.vehicle.VehicleTicker;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.teleport.TeleportUtil;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.Dimension;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.geyser.BoarChunk;
import ac.boar.anticheat.util.geyser.BoarChunkSection;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;

public class AuthInputPackets extends TeleportHandler implements PacketListener {

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        // Clear the last input's matched claim first, even if this input is cancelled below. A claim is valid for one input only.
        if (event.getPacket() instanceof PlayerAuthInputPacket && event.getPlayer().vehicleData != null) {
            event.getPlayer().vehicleData.beginMatchedClaimAttempt();
        }
        if (event.isCancelled()) {
            return;
        }

        final BoarPlayer player = event.getPlayer();
        if (!(event.getPacket() instanceof PlayerAuthInputPacket packet)) {
            return;
        }

        player.sinceLoadingScreen++;
        player.inputArrivalNs = System.nanoTime();
        player.getLatencyUtil().observeInputArrival(player.inputArrivalNs);

        // -------------------------------------------------------------------------
        // Timer check start here.
        final long claimedTick = packet.getTick();

        if (claimedTick < 0) { // Impossible, no way this can happen.
            if (player.disableMitigations()) {
                player.getCheckHolder().manuallyFail(BadPacketA.class, "impossible tick=" + claimedTick);
            } else {
                player.kick("Impossible tick id=" + claimedTick);
            }
            return;
        }

        player.tick++;
        player.simulationFrame = claimedTick;
        player.sinceAuthInput = System.currentTimeMillis();

        final Timer timer = (Timer) player.getCheckHolder().get(Timer.class);
        final Reach reach = (Reach) player.getCheckHolder().get(Reach.class);
        if (timer != null && timer.isInvalid()) {
            if (!player.disableMitigations()) {
                event.setCancelled(true);
                if (reach != null) {
                    reach.invalidatePending();
                }
                Boar.debug(player.getSession().name() + ": [movement-debug] cancelled auth-input reason=timer tick=" + player.tick + " packetTick=" + packet.getTick() + " pos=" + packet.getPosition() + " delta=" + packet.getDelta(), Boar.DebugMessage.WARNING);
                return;
            }
        }

        // Timer check end here.
        // -------------------------------------------------------------------------

        player.acceptedInputCount++;

        if (player.serverBreakBlockValidator != null) {
            player.serverBreakBlockValidator.handle(packet);
        }

        LegacyAuthInputPackets.processAuthInput(player, packet, true);
        LegacyAuthInputPackets.updateUnvalidatedPosition(player, packet);

        if (player.tick <= player.ackDebugUntilTick || (Boar.getConfig().debugMode() && player.getFlagTracker().has(EntityFlag.USING_ITEM))) {
            final var input = packet.getInputData();
            Boar.debug(player.getSession().name() + ": [ack-debug] tick=" + player.tick + " t=" + (System.nanoTime() / 1_000_000L % 100_000L)
                    + "ms INPUT clientSprinting=" + input.contains(PlayerAuthInputData.SPRINTING)
                    + (input.contains(PlayerAuthInputData.START_SPRINTING) ? " START_SPRINTING" : "")
                    + (input.contains(PlayerAuthInputData.STOP_SPRINTING) ? " STOP_SPRINTING" : "")
                    + (input.contains(PlayerAuthInputData.START_USING_ITEM) ? " START_USING_ITEM" : "")
                    + (input.contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION) && packet.getItemUseTransaction() != null
                        ? " PERFORM_ITEM_INTERACTION(action=" + packet.getItemUseTransaction().getActionType() + ")" : "")
                    + " boarSprinting=" + player.getFlagTracker().has(EntityFlag.SPRINTING)
                    + " boarUsing=" + player.getFlagTracker().has(EntityFlag.USING_ITEM)
                    + " boarSpeed=" + player.getSpeed(), Boar.DebugMessage.INFO);
        }

        final int chunkX = GenericMath.floor(player.position.x) >> 4;
        final int chunkZ = GenericMath.floor(player.position.z) >> 4;
        final BoarChunk chunkInside = player.compensatedWorld.getChunk(chunkX, chunkZ);
        if (chunkInside != null) {
            player.insideUnloadedChunk = false;
            if (Boar.DEBUG_CHUNKS && !chunkInside.hasAllSections() && chunkInside.warnForMissingSections()) {
                StringBuilder missing = null;
                for (int idx = 0; idx < chunkInside.sections().length; idx++) {
                    BoarChunkSection sec = chunkInside.getSection(idx);
                    if (sec == null) {
                        if (missing == null) missing = new StringBuilder("["); else missing.append(", ");
                        missing.append(idx);
                    }
                }
                if (missing != null) missing.append("]"); else missing = new StringBuilder("[]");
                Boar.chunkDebug(player.getSession().name() + ": inside loaded chunk but has missing sub-chunks: " + missing, Boar.DebugMessage.WARNING);
            }
        } else {
            player.insideUnloadedChunk = true;
        }


        // Start a fresh movement trace for this tick, with a snapshot of the start state.
        player.getMovementTrace().begin();

        if (reach != null) { // null when the Reach check is disabled via disabled-checks - don't NPE.
            reach.validatePending();
        }

        player.tick();

        // Read the old input phase first. updateFromInput overwrites inputClaimsClientPrediction.
        final boolean horseDeathExit = prepareHorseDeathExit(player, packet);
        if (player.vehicleData != null) {
            player.vehicleData.updateFromInput(packet);
        }
        player.horseJumpInput.tick(player, claimedTick, packet.getInputData().contains(PlayerAuthInputData.JUMPING));

        if (player.vehicleData != null) {
            // Teleports are not handled while riding yet. Keep the last one for dismount.
            final TeleportData accepted = player.getTeleportUtil().takeAcceptedTeleport();
            if (accepted != null) {
                player.vehicleData.parkedTeleport = accepted;
            }
            // Still steering the vehicle after it, so it was not a dismount teleport.
            if (player.vehicleData.inputClaimsClientPrediction) {
                player.vehicleData.parkedTeleport = null;
            }

            // The client can leave on its own before the server answers, so check this before serverDismount.
            // A server teleport the client has not handled yet comes later, on foot.
            final ExitStart localExit = horseDeathExit ? null : localExitStart(player);
            switch (localExit != null ? DismountState.LOCAL_EXIT : this.serverDismount(player, packet)) {
                case LOCAL_EXIT -> {
                    // horseJumpInput already has the client's reset from the leave request (VehiclePackets), so keep it.
                    leaveRide(player, packet, localExit);
                    player.getMovementTrace().log("path: vehicle, the client left it on its own before the server answered, feet=" + localExit.feet()
                            + " actorY=" + localExit.actorY());
                }
                case WAITING_FOR_TELEPORT -> {
                    if (horseDeathExit) {
                        player.vehicleData.logHorseDeath(player, "decline reason=pending-teleport");
                    }
                    player.vehicleData.heldTeleportHandled |= packet.getInputData().contains(PlayerAuthInputData.HANDLE_TELEPORT);
                    player.tickFollowerHistories();
                    player.getMovementTrace().log("path: vehicle, left it before the dismount teleport arrived, held");
                    player.compensatedWorld.cleanChunksAtPlayerPosition();
                    return;
                }
                case LEFT -> {
                    if (horseDeathExit) {
                        player.vehicleData.logHorseDeath(player, "decline reason=server-dismount-teleport");
                    }
                    final TeleportData teleport = player.vehicleData.parkedTeleport;
                    final boolean handledEarlier = player.vehicleData.heldTeleportHandled
                            && !packet.getInputData().contains(PlayerAuthInputData.HANDLE_TELEPORT);
                    player.vehicleData.invalidateJumpInputUnlessLeaving(player);
                    player.vehicleData = null;
                    player.getTeleportUtil().apply(teleport);
                    // This input already has the player position, not the vehicle position.
                    player.unvalidatedPosition = new Vec3(packet.getPosition().down(player.getYOffset()));
                    player.getMovementTrace().log("path: vehicle, left it with the dismount teleport " + teleport.getPosition());
                    if (handledEarlier) {
                        // The client handled the teleport on the input we held (it had HANDLE_TELEPORT, so that tick skipped travel).
                        // Run that skipped tick now. This input then moves normally below.
                        // In-game testing showed that without this, we also skipped travel on this input and fell one tick behind the client.
                        this.processTeleport(player);
                        player.getMovementTrace().log("path: the client handled the dismount teleport on the held input; ran that tick, now moving normally");
                    }
                }
                case RIDING -> {
                    final ExitStart followerStart = horseDeathExit ? null : followerDeathExitStart(player, packet);
                    if (followerStart != null) {
                        final VehicleData vehicle = player.vehicleData;
                        vehicle.invalidateJumpInputUnlessLeaving(player);
                        leaveRide(player, packet, followerStart);
                        vehicle.logFollowerDeath(player, "decision result=left feet=" + followerStart.feet() + " actorY=" + followerStart.actorY());
                    } else if (horseDeathExit) {
                        final VehicleData vehicle = player.vehicleData;
                        final ExitStart start = vehicle.parkedTeleport == null ? exitStart(player, player.position, false, null, 0) : null;
                        if (start == null) {
                            vehicle.logHorseDeath(player, "decline reason=" + (vehicle.parkedTeleport != null ? "parked-teleport" : "geometry"));
                        } else {
                            vehicle.invalidateJumpInputUnlessLeaving(player);
                            leaveRide(player, packet, start);
                            vehicle.logHorseDeath(player, "decision result=left feet=" + start.feet() + " actorY=" + start.actorY());
                        }
                    }
                }
            }
        }

        player.tickFollowerHistories();

        if (player.vehicleData != null) {
            player.getMovementTrace().log("path: vehicle");
            new VehiclePredictionRunner(player).run();
        } else if (player.getEntity().bedPosition() != null) {
            player.getMovementTrace().log("path: in bed, skipped");
            return;
        } else if (this.processTeleport(player)) {
            player.getMovementTrace().log("path: teleport, skipped ordinary travel");
        } else if (player.staticSeat != null && player.staticSeat.prepare(player, packet)) {
            // Static seat does not move.
        } else if (player.dead && player.certainVelocity == null) {
            // From vanilla client - Player::isImmobile is true at 0 health unless the knocked-back-on-death flag is set, and the
            // client reports a fixed position with a zero delta
            player.getMovementTrace().log("path: dead, no movement expected");
            processImmobile(player);
        } else if (player.inLoadingScreen) {
            // Prevent abuse with clients that decide to never send the end loading screen for whatever reason (???)
            // The client itself is immobile on a dimension change (LocalPlayer::changeDimension adds ActorIsImmobileFlagComponent)
            player.getMovementTrace().log("path: loading screen, no movement expected");
            processImmobile(player);
        } else if (player.insideUnloadedChunk) {
            player.getMovementTrace().log("path: unloaded chunk, no movement expected");
            processImmobile(player);
        } else {
            if (player.isMovementExempted() || player.sinceLoadingScreen < 2) {
                player.getMovementTrace().log("path: exempted (movementExempt=" + player.isMovementExempted()
                        + " inLoadingScreen=" + player.inLoadingScreen
                        + " sinceLoadingScreen=" + player.sinceLoadingScreen + ")");
                processExempted(player);
            } else {
                player.getMovementTrace().log("path: prediction");
                final PredictionState start = PredictionState.capture(player);
                final PredictionRunner runner = new PredictionRunner(player);
                runner.run();
                CollisionRetry.attempt(player, start);
                BoatTopRetry.attempt(player, start);
                runner.capturePrediction();
            }
        }

        player.insideUnloadedChunk = !player.compensatedWorld.isChunkLoadedAt(player.position.x, player.position.z);
        player.compensatedWorld.cleanChunksAtPlayerPosition();

        LegacyAuthInputPackets.doPostPrediction(player, packet);
    }

    private static boolean prepareHorseDeathExit(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        final VehicleData vehicle = player.vehicleData;
        if (vehicle == null) {
            return false;
        }
        if (packet.getInputData().contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)) {
            if (vehicle.isHorseDeathExitMount(player, vehicle.mountedEntity)) {
                vehicle.horseDeathInputSeen = true;
            }
            return false;
        }
        if (!vehicle.horseDeathAuthorized) {
            if (vehicle.horseDeathInputSeen && !vehicle.horseDeathDecided) {
                vehicle.horseDeathDecided = true;
                vehicle.logHorseDeath(player, "latch reason=unauthorized-first-flagless");
            }
            return false;
        }
        final boolean eligible = vehicle.canEndHorseDeath(player, vehicle.mountedEntity);
        vehicle.horseDeathAuthorized = false;
        vehicle.horseDeathDecided = true;
        vehicle.logHorseDeath(player, "decision eligible=" + eligible);
        if (!eligible) {
            vehicle.logHorseDeath(player, "decline reason=changed-ride-authority");
        }
        return eligible;
    }

    // The rider's exit pose when it leaves a vehicle (after a mount died, or on its own): feet position and the actor (eye) Y the client stores.
    private record ExitStart(Vec3 feet, float actorY) {}

    // Ends the ride on this input, with the rider at start. Clear vehicleData first: setPos and getYOffset read it.
    private static void leaveRide(final BoarPlayer player, final PlayerAuthInputPacket packet, final ExitStart start) {
        player.vehicleData = null;
        player.setPos(start.feet());
        // Store both floats the client uses. Undoing the feet subtraction can lose a bit of precision.
        player.nativeOriginY = start.actorY();
        player.velocity = Vec3.ZERO.clone();
        player.certainVelocity = null;
        player.unvalidatedPosition = new Vec3(packet.getPosition().down(player.getYOffset()));
    }

    // Follower death exit, see VehicleData.isFollowerDeathExitMount.
    // This decides by the claimed Y, not by time. Until followerDeathDeadline:
    // - An input without IN_CLIENT_PREDICTED_IN_VEHICLE whose Y is closer to the seat than to the client's exit start keeps the ride.
    //   The seat check judges it and the authorization stays.
    // - The first input closer to the exit start ends the ride there. Normal on-foot prediction then judges that input.
    // - A tie keeps the ride.
    private static ExitStart followerDeathExitStart(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        // Not null: the only caller runs inside onPacketReceived's player.vehicleData != null block.
        final VehicleData vehicle = player.vehicleData;
        if (!vehicle.followerDeathAuthorized) {
            return null;
        }
        if (player.tick > vehicle.followerDeathDeadline) {
            vehicle.declineFollowerDeath(player, "expired");
            return null;
        }
        if (packet.getInputData().contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)) {
            vehicle.logFollowerDeath(player, "skip reason=predicted-input");
            return null;
        }
        final EntityCache cache = vehicle.mountedEntity;
        if (!vehicle.canEndFollowerDeath(player, cache)) {
            vehicle.declineFollowerDeath(player, "changed-ride-authority clientSimulates=" + VehicleTicker.clientSimulates(player, vehicle)
                    + " ticks=" + vehicle.ticks);
            return null;
        }
        if (vehicle.parkedTeleport != null) {
            // The server already sent the dismount teleport for this ride. The normal LEFT path (serverDismount) ends it.
            vehicle.declineFollowerDeath(player, "parked-teleport");
            return null;
        }
        final var current = cache.getCurrent();
        final var stationaryCamel = vehicle.guardedStationaryCamelExit(player, cache);
        final Vec3 claimed = vehicle.guardedFollowerDeathPosition(player, cache);
        final Vec3 actor;
        if (stationaryCamel != null) {
            actor = new Vec3(stationaryCamel.actor());
            vehicle.logFollowerDeath(player, "geometry source=frozen-stationary-camel");
        } else if (claimed != null) {
            // The client drove this mount (a saddled donkey or mule). It leaves from its own last vehicle position, which our physics accepted
            // (the matched claim). In-game testing showed the server's position for the mount can be behind the client's, even when it stands still.
            actor = new Vec3(claimed.x, claimed.y + cache.getYOffset(), claimed.z);
            vehicle.logFollowerDeath(player, "geometry source=matched-claim");
        } else if (current == null || vehicle.followerDeathPositionChanged || current.getPos().distanceTo(current.getPrevPos()) != 0) {
            // The actor is moving and the client did not drive it. We do not know where the client saw it, so keep the ride.
            // "Moving" means the position changed. Empty and rotation-only updates also restart the interpolation counter, but do not move it.
            vehicle.logFollowerDeath(player, "skip reason=actor-moving");
            return null;
        } else {
            // The actor stands still. Use its current position.
            actor = new Vec3(current.getPos().x, current.getPos().y + cache.getYOffset(), current.getPos().z);
        }
        final ExitStart start = exitStart(player, actor, true, stationaryCamel == null ? null : stationaryCamel.yaw(), 0);
        if (start == null) {
            vehicle.declineFollowerDeath(player, "geometry");
            return null;
        }
        final float claimedY = packet.getPosition().getY();
        final float seatY = actor.y + player.seatOffset.getY();
        final float toStart = Math.abs(claimedY - start.actorY()), toSeat = Math.abs(claimedY - seatY);
        if (!(toStart < toSeat)) {
            vehicle.logFollowerDeath(player, "skip reason=seat-row claimedY=" + claimedY + " seatY=" + seatY + " startY=" + start.actorY());
            return null;
        }
        vehicle.followerDeathAuthorized = false;
        vehicle.followerDeathDecided = true;
        return start;
    }

    /**
     * Where the client puts itself when it leaves a vehicle on its own, after it sent InteractPacket LEAVE_VEHICLE (pendingLeave).
     * The client does not wait for the server: ExitVehicleSystemImpl::_tickExitVehicle (0x10519c508) runs for every vehicle without the
     * server-only dismount flag (DoesServerAuthOnlyDismountFlagComponent, actor flag 123). Its next input has no IN_CLIENT_PREDICTED_IN_VEHICLE
     * and is on foot. If the server refuses the leave, it sends the link again, which is a new mount (handleVehicleSet).
     * <p>
     * Modeled: only the first candidate of the leave exit pattern (VehicleUtils::testPosFollowingEjectPattern). For a still vehicle
     * (horizontal move squared at most 9.801e-05) the directions are (-1,0,0) and (0,0,-1). The first try is (0,0,-1), no vertical offset.
     * The cell and seat checks are exitStart's, with exitZ -1.
     * Not modeled (we keep the ride and the seat check judges the input):
     * - A vehicle that moved in one of our last two ticks (the client then turns the directions toward the move).
     * - A blocked first candidate, a floor that is not a full block (a boat on water), a seat that is not centered.
     * - A vehicle the client does not simulate (VehicleTicker.clientSimulates).
     * - A server that sets the server-only dismount flag. The input then keeps IN_CLIENT_PREDICTED_IN_VEHICLE, so we never get here.
     * - The legacy actor pattern, used for worlds older than NetherUpdate_U3 (VehicleUtils::getActorExitPatternStrategy).
     */
    private static ExitStart localExitStart(final BoarPlayer player) {
        // Not null: the only caller runs inside onPacketReceived's player.vehicleData != null block.
        final VehicleData vehicle = player.vehicleData;
        if (!vehicle.pendingLeave || vehicle.inputClaimsClientPrediction || vehicle.localExitDecided) {
            return null;
        }
        vehicle.localExitDecided = true;
        final String decline;
        ExitStart start = null;
        if (!VehicleTicker.clientSimulates(player, vehicle)) {
            decline = "not-client-simulated";
        } else if (vehicle.parkedTeleport != null) {
            // The client handled a server teleport, which ends the ride there. The LEFT path (serverDismount) does that.
            decline = "parked-teleport";
        } else if (vehicle.stillTicks < 2) {
            decline = "vehicle-moved stillTicks=" + vehicle.stillTicks;
        } else if (player.position.x != vehicle.position.x || player.position.y != vehicle.position.y || player.position.z != vehicle.position.z) {
            decline = "position-changed";
        } else {
            start = exitStart(player, player.position, false, null, -1);
            decline = start == null ? "geometry" : null;
        }
        if (start == null) {
            Boar.debug(player.getSession().name() + ": [movement-debug] ride: local exit declined reason=" + decline + " runtime=" + vehicle.vehicleRuntimeId,
                    Boar.DebugMessage.INFO);
        }
        return start;
    }

    // The server sends a server-moved vehicle (pig, donkey, camel, ...) this far above its block (Bedrock entity definition offset).
    private static final float SERVER_ENTITY_OFFSET = 0.00001F;

    /**
     * Where the client puts the rider when it leaves a vehicle: a dead mount (horse or follower), or a local exit (localExitStart).
     * Returns null when we cannot model it.
     * Only the client's first exit candidate is modeled. Needs a centered seat (unless frozenYaw is given) and a known, clear full-block floor.
     * <p>
     * serverMoved: true for followers (the server moves them), so Y may sit one or two SERVER_ENTITY_OFFSET steps above the block top,
     * for example 30.00001 or 30.00002. False for a horse death exit and the local exit: Y must be the block top.
     * The seat math still uses the raw server Y, like the client.
     * <p>
     * exitZ: the Z of the first exit candidate, 0 or -1 (one block -Z). It moves both the probed cell and the result.
     * Client: the candidate check lambda next to ExitVehicleSystemImpl::_tickExitVehicle (0x10519dcfc) floors (block center + candidate)
     * to get the cell. The result adds the candidate to the seat.
     */
    private static ExitStart exitStart(final BoarPlayer player, final Vec3 vehicle, final boolean serverMoved, final Float frozenYaw, final int exitZ) {
        final Vector3f seatOffset = player.seatOffset;
        final float width = player.dimensions.width(), height = player.dimensions.height();
        final float riderOffset = player.getPlayerYOffset();
        if (frozenYaw == null && (seatOffset.getX() != 0 || seatOffset.getZ() != 0) || !MathUtil.finite(seatOffset)
                || frozenYaw != null && !Float.isFinite(frozenYaw) || !MathUtil.finite(vehicle)
                || !Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(riderOffset) || width <= 0 || height <= 0) {
            return null;
        }
        final float seatX;
        final float seatZ;
        if (frozenYaw == null) {
            seatX = vehicle.x;
            seatZ = vehicle.z;
        } else {
            final Vector3f shift = MathUtil.seatShift(frozenYaw, seatOffset);
            seatX = vehicle.x + shift.getX();
            seatZ = vehicle.z + shift.getZ();
        }
        final float seatY = vehicle.y + seatOffset.getY();
        // Use the rider's own position and box, not the horse's box. Each step rounds to float.
        final float minY = seatY - riderOffset, maxY = minY + height;
        final float halfWidth = width / 2F, minX = seatX - halfWidth, maxX = seatX + halfWidth;
        // The client moves this box to the seat first. Our box is already at the seat, so that move is zero.
        final int probeX = GenericMath.floor(vehicle.x), probeY = GenericMath.floor(vehicle.y), probeZ = GenericMath.floor(vehicle.z) + exitZ;
        // Floor checks use the block top. The seat math above uses the raw server Y.
        final float floorTop = (float) probeY;
        if (serverMoved && vehicle.y != floorTop) {
            final float oneOffset = serverOffsetY(probeY, vehicle.y);
            final float allowed = vehicle.y == oneOffset + SERVER_ENTITY_OFFSET ? vehicle.y : oneOffset;
            if (allowed != vehicle.y) {
                return null;
            }
        } else if (floorTop != vehicle.y) {
            return null;
        }
        final float alignY = (float) probeY - minY;
        // On a full-block floor the client's floor correction is zero. The modeled exit candidate has no Y offset. So neither changes Y here.
        final float selected = seatY + alignY;
        final float actorY = selected + .001F, feetY = actorY - riderOffset;
        if (!Float.isFinite(actorY) || !Float.isFinite(feetY)) {
            return null;
        }
        final float nativeWidth = Math.min(maxX - minX, 1F);
        final float nativeHeight = maxY - minY;
        final float probeCenterX = (float) probeX + .5F, probeCenterZ = (float) probeZ + .5F;
        final Box probeBox = new Box(probeCenterX - nativeWidth / 2F, (float) probeY, probeCenterZ - nativeWidth / 2F,
                probeCenterX + nativeWidth / 2F, (float) probeY + nativeHeight, probeCenterZ + nativeWidth / 2F);
        final Vec3 feet = new Vec3(seatX, feetY, exitZ == 0 ? seatZ : seatZ + exitZ);
        final Box finalBox = player.dimensions.getBoxAt(feet);
        try (final var reads = player.compensatedWorld.trackBlockReads()) {
            final BoarBlockState floor = knownExitBlock(player, probeX, probeY - 1, probeZ, 0);
            if (floor == null) {
                return null;
            }
            // The floor must be one full-block shape.
            final var shapes = floor.findCollision(player, Vector3i.from(probeX, probeY - 1, probeZ), probeBox, false);
            if (shapes.size() != 1) {
                return null;
            }
            final Box shape = shapes.getFirst();
            if (shape.minX != probeX || shape.maxX != probeX + 1F || shape.minY != probeY - 1F || shape.maxY != floorTop
                    || shape.minZ != probeZ || shape.maxZ != probeZ + 1F) {
                return null;
            }
            if (!clearKnownExitBox(player, probeBox) || !clearKnownExitBox(player, finalBox) || !reads.known()) {
                return null;
            }
        }
        return new ExitStart(feet, actorY);
    }

    // The exact float the server sends for block Y plus the entity offset. Absolute moves add in double. Delta moves add in float.
    private static float serverOffsetY(final int blockY, final float y) {
        final float delta = (float) blockY + SERVER_ENTITY_OFFSET;
        return y == delta ? delta : (float) ((double) blockY + (double) SERVER_ENTITY_OFFSET);
    }

    // The block at this cell, or null when its chunk data or its block mapping is not known.
    private static BoarBlockState knownExitBlock(final BoarPlayer player, final int x, final int y, final int z, final int layer) {
        final var raw = player.compensatedWorld.getKnownMappedRawBlockAt(x, y, z, layer);
        if (raw.isEmpty()) {
            return null;
        }
        final int rawId = raw.getAsInt();
        player.compensatedWorld.observeBlockRead(x, y, z, layer);
        return BoarBlockState.create(player.fromRawBlockId(rawId), Vector3i.from(x, y, z), layer);
    }

    // True when every block around the box is known and none of them overlaps it. A block that only shares a face with the box is fine.
    private static boolean clearKnownExitBox(final BoarPlayer player, final Box box) {
        // Also check the cells around the box. Some block shapes stick out of their own block. Normal movement checks them too.
        for (int x = GenericMath.floor(box.minX - 1F); x <= GenericMath.floor(box.maxX + 1F); x++) {
            for (int z = GenericMath.floor(box.minZ - 1F); z <= GenericMath.floor(box.maxZ + 1F); z++) {
                for (int y = GenericMath.floor(box.minY - 1F); y <= GenericMath.floor(box.maxY + 1F); y++) {
                    for (int layer = 0; layer <= 1; layer++) {
                        final BoarBlockState block = knownExitBlock(player, x, y, z, layer);
                        if (block == null || !block.findCollision(player, Vector3i.from(x, y, z), box, true).isEmpty()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private enum DismountState {
        RIDING, WAITING_FOR_TELEPORT, LEFT, LOCAL_EXIT
    }

    // The client leaves a vehicle it simulates once it gets the dismount teleport. This can be some inputs before the server removes the link.
    // After that, the client stops sending the predicted vehicle. If no server teleport came this ride, we keep simulating.
    private DismountState serverDismount(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        final VehicleData vehicle = player.vehicleData;
        final boolean clientSimulates = VehicleTicker.clientSimulates(player, vehicle);
        // A follower's normal MovePlayer teleport ends the ride before a later REMOVE link.
        // Only a server teleport accepted for this exact ride allows this input.
        if (!clientSimulates && !vehicle.inputClaimsClientPrediction && vehicle.parkedTeleport != null
                && vehicle.parkedTeleport.isOrdinaryTeleportForRide(vehicle) && packet.getInputData().contains(PlayerAuthInputData.HANDLE_TELEPORT)) {
            return DismountState.LEFT;
        }
        if (!clientSimulates || vehicle.inputClaimsClientPrediction) {
            return DismountState.RIDING;
        }

        final TeleportUtil teleports = player.getTeleportUtil();
        if (teleports.getPendingTeleports() > 0 && teleports.getLastTeleportSince(vehicle.mountTick) != null) {
            return DismountState.WAITING_FOR_TELEPORT;
        }

        return vehicle.parkedTeleport != null && vehicle.parkedTeleport != vehicle.preDriverSwitchTeleport ? DismountState.LEFT : DismountState.RIDING;
    }

    @Override
    public void onPacketSend(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        switch (event.getPacket()) {
            case ChangeDimensionPacket packet -> {
                player.staticSeatTracker.resetSent(player);
                int dimensionId = packet.getDimension();
                final Dimension dimension = DimensionUtil.dimensionFromId(dimensionId);

                player.pendingDimensionSwitches++;
                // The client moves to this position (LocalPlayer::changeDimension). Without this, holding still in the
                // loading screen would keep the old position.
                player.getTeleportUtil().queue(new TeleportData(new Vec3(packet.getPosition()), false, TeleportData.Source.OTHER));
                player.queueAcknowledgment(new DimensionSwitchAck(dimension, packet.getLoadingScreenId()));
            }
            case MovePlayerPacket packet when packet.getRuntimeEntityId() == player.runtimeEntityId && packet.getMode() != MovePlayerPacket.Mode.HEAD_ROTATION -> {
                Boar.debug(player.getSession().name() + ": [movement-debug] queued server teleport source=MovePlayerPacket mode="
                        + packet.getMode() + " cause=" + packet.getTeleportationCause() + " pos=" + packet.getPosition()
                        + " tick=" + player.tick + " dead=" + player.dead, Boar.DebugMessage.WARNING);
                final TeleportData.Source source = switch (packet.getMode()) {
                    case NORMAL -> TeleportData.Source.MOVE_PLAYER_NORMAL;
                    case RESPAWN -> TeleportData.Source.MOVE_PLAYER_RESPAWN;
                    default -> TeleportData.Source.MOVE_PLAYER_TELEPORT;
                };
                final TeleportData.MovePlayerContext context = new TeleportData.MovePlayerContext(packet.getMode(), packet.getRidingRuntimeEntityId(),
                        packet.getTick(), player.vehicleData);
                player.getTeleportUtil().queue(new TeleportData(new Vec3(packet.getPosition()), packet.isOnGround(), source, context));
            }
            // The vanilla server sends runtime id 0 in the player's own RespawnPacket (Player::recheckSpawnPosition) so accept 0 as well as the player's id.
            case RespawnPacket packet when (packet.getRuntimeEntityId() == player.runtimeEntityId || packet.getRuntimeEntityId() == 0) && packet.getState() != RespawnPacket.State.CLIENT_READY -> {
                player.staticSeatTracker.resetSent(player);
                player.sendLatencyStack(new RespawnStateAck(packet.getState()));
                if (packet.getState() == RespawnPacket.State.SERVER_READY) {
                    Boar.debug(player.getSession().name() + ": [movement-debug] queued server teleport source=RespawnPacket runtimeId="
                            + packet.getRuntimeEntityId() + " pos=" + packet.getPosition() + " tick=" + player.tick, Boar.DebugMessage.WARNING);
                    player.getTeleportUtil().queue(new TeleportData(new Vec3(packet.getPosition()), true, TeleportData.Source.RESPAWN));
                }
            }
            default -> {}
        }
    }
}
