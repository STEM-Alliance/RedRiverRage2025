// Copyright (c) 2025 FRC 6328
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by an MIT-style
// license that can be found in the LICENSE file at
// the root directory of this project.

package org.littletonrobotics.frc2025.subsystems.drive;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.simulation.DCMotorSim;

/**
 * Physics sim implementation of module IO. The sim models are configured using a set of module
 * constants from Phoenix. Simulation is always based on voltage control.
 */
public class ModuleIOSim implements ModuleIO {
  private static final DCMotor driveMotorModel = DCMotor.getKrakenX60Foc(1);
  private static final DCMotor turnMotorModel = DCMotor.getKrakenX60Foc(1);

  private final DCMotorSim driveSim =
      new DCMotorSim(
          LinearSystemId.createDCMotorSystem(driveMotorModel, 0.025, ModuleIOComp.driveReduction),
          driveMotorModel);
  private final DCMotorSim turnSim =
      new DCMotorSim(
          LinearSystemId.createDCMotorSystem(turnMotorModel, 0.004, ModuleIOComp.turnReduction),
          turnMotorModel);

  private boolean driveClosedLoop = false;
  private boolean turnClosedLoop = false;
  private PIDController driveController = new PIDController(0, 0, 0);
  private PIDController turnController = new PIDController(0, 0, 0);
  private double driveFFVolts = 0;
  private double driveAppliedVolts = 0.0;
  private double turnAppliedVolts = 0.0;

  public ModuleIOSim() {
    // Enable wrapping for turn PID
    turnController.enableContinuousInput(-Math.PI, Math.PI);
  }

  @Override
  public void updateInputs(ModuleIOInputs inputs) {
    // Run closed-loop control
    if (driveClosedLoop) {
      driveAppliedVolts =
          driveFFVolts + driveController.calculate(driveSim.getAngularVelocityRadPerSec());
    } else {
      driveController.reset();
    }
    if (turnClosedLoop) {
      turnAppliedVolts = turnController.calculate(turnSim.getAngularPositionRad());
    } else {
      turnController.reset();
    }

    // Update simulation state
    driveSim.setInputVoltage(MathUtil.clamp(driveAppliedVolts, -12.0, 12.0));
    turnSim.setInputVoltage(MathUtil.clamp(turnAppliedVolts, -12.0, 12.0));
    driveSim.update(0.02);
    turnSim.update(0.02);

    // Update drive inputs
    inputs.driveConnected = true;
    inputs.drivePositionRad = driveSim.getAngularPositionRad();
    inputs.driveVelocityRadPerSec = driveSim.getAngularVelocityRadPerSec();
    inputs.driveAppliedVolts = driveAppliedVolts;
    inputs.driveCurrentAmps = Math.abs(driveSim.getCurrentDrawAmps());

    // Update turn inputs
    inputs.turnConnected = true;
    inputs.turnEncoderConnected = true;
    inputs.turnAbsolutePosition = new Rotation2d(turnSim.getAngularPositionRad());
    inputs.turnPosition = new Rotation2d(turnSim.getAngularPositionRad());
    inputs.turnVelocityRadPerSec = turnSim.getAngularVelocityRadPerSec();
    inputs.turnAppliedVolts = turnAppliedVolts;
    inputs.turnCurrentAmps = Math.abs(turnSim.getCurrentDrawAmps());

    // Update odometry inputs (50Hz because high-frequency odometry in sim doesn't matter)
    inputs.odometryTimestamps = new double[] {Timer.getFPGATimestamp()};
    inputs.odometryDrivePositionsRad = new double[] {inputs.drivePositionRad};
    inputs.odometryTurnPositions = new Rotation2d[] {inputs.turnPosition};
  }

  @Override
  public void runDriveOpenLoop(double output) {
    driveClosedLoop = false;
    driveAppliedVolts = output;
  }

  @Override
  public void runTurnOpenLoop(double output) {
    turnClosedLoop = false;
    turnAppliedVolts = output;
  }

  @Override
  public void runDriveVelocity(double velocityRadPerSec, double feedforward) {
    driveClosedLoop = true;
    driveFFVolts = feedforward;
    driveController.setSetpoint(velocityRadPerSec);
  }

  @Override
  public void runTurnPosition(Rotation2d rotation) {
    turnClosedLoop = true;
    turnController.setSetpoint(rotation.getRadians());
  }
}
