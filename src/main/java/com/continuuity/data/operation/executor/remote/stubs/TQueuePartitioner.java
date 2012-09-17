/**
 * Autogenerated by Thrift Compiler (0.8.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.continuuity.data.operation.executor.remote.stubs;


import java.util.Map;
import java.util.HashMap;
import org.apache.thrift.TEnum;

public enum TQueuePartitioner implements org.apache.thrift.TEnum {
  RANDOM(0),
  HASH(1),
  LONGMOD(2);

  private final int value;

  private TQueuePartitioner(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  public static TQueuePartitioner findByValue(int value) { 
    switch (value) {
      case 0:
        return RANDOM;
      case 1:
        return HASH;
      case 2:
        return LONGMOD;
      default:
        return null;
    }
  }
}
