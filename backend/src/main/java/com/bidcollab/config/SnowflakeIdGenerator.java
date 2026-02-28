package com.bidcollab.config;

import java.io.Serializable;
import java.time.Instant;
import java.util.Properties;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;
import org.hibernate.id.Configurable;

/**
 * 雪花ID生成器（64-bit）：
 * 1bit 符号位 + 41bit 时间戳 + 5bit 数据中心 + 5bit 机器 + 12bit 序列。
 */
public class SnowflakeIdGenerator implements IdentifierGenerator, Configurable {

  private static final long EPOCH = 1704038400000L; // 2024-01-01T00:00:00Z
  private static final long WORKER_ID_BITS = 5L;
  private static final long DATACENTER_ID_BITS = 5L;
  private static final long SEQUENCE_BITS = 12L;

  private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
  private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
  private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

  private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
  private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
  private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

  private long workerId = 1L;
  private long datacenterId = 1L;
  private long sequence = 0L;
  private long lastTimestamp = -1L;

  @Override
  public void configure(Type type, Properties parameters, ServiceRegistry serviceRegistry) throws HibernateException {
    String worker = parameters.getProperty("workerId");
    String datacenter = parameters.getProperty("datacenterId");
    if (worker != null) {
      workerId = Long.parseLong(worker);
    }
    if (datacenter != null) {
      datacenterId = Long.parseLong(datacenter);
    }
    if (workerId < 0 || workerId > MAX_WORKER_ID) {
      throw new HibernateException("Invalid workerId: " + workerId);
    }
    if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
      throw new HibernateException("Invalid datacenterId: " + datacenterId);
    }
  }

  @Override
  public synchronized Serializable generate(SharedSessionContractImplementor session, Object object)
      throws HibernateException {
    long timestamp = currentTimestamp();

    if (timestamp < lastTimestamp) {
      throw new HibernateException("Clock moved backwards. Refusing to generate id for "
          + (lastTimestamp - timestamp) + "ms");
    }

    if (timestamp == lastTimestamp) {
      sequence = (sequence + 1) & SEQUENCE_MASK;
      if (sequence == 0) {
        timestamp = tilNextMillis(lastTimestamp);
      }
    } else {
      sequence = 0L;
    }

    lastTimestamp = timestamp;

    long id = ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
        | (datacenterId << DATACENTER_ID_SHIFT)
        | (workerId << WORKER_ID_SHIFT)
        | sequence;
    return id;
  }

  private long tilNextMillis(long lastTs) {
    long ts = currentTimestamp();
    while (ts <= lastTs) {
      ts = currentTimestamp();
    }
    return ts;
  }

  private long currentTimestamp() {
    return Instant.now().toEpochMilli();
  }
}
