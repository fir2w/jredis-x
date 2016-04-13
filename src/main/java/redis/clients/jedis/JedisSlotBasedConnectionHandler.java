package redis.clients.jedis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.Set;

import redis.clients.jedis.exceptions.JedisConnectionException;

public class JedisSlotBasedConnectionHandler extends JedisClusterConnectionHandler {

  public JedisSlotBasedConnectionHandler(Set<HostAndPort> nodes,
      final GenericObjectPoolConfig poolConfig, int timeout) {
    this(nodes, poolConfig, timeout, timeout);
  }

  public JedisSlotBasedConnectionHandler(Set<HostAndPort> nodes,
      final GenericObjectPoolConfig poolConfig, int connectionTimeout, int soTimeout) {
    super(nodes, poolConfig, connectionTimeout, soTimeout);
  }

  public Jedis getConnection() {
    // In antirez's redis-rb-cluster implementation,
    // getRandomConnection always return valid connection (able to
    // ping-pong)
    // or exception if all connections are invalid

    List<JedisPool> pools = getShuffledNodesPool();

    for (JedisPool pool : pools) {
      Jedis jedis = null;
      try {
        jedis = pool.getResource();

        if (jedis == null) {
          continue;
        }

        String result = jedis.ping();

        if (result.equalsIgnoreCase("pong")) return jedis;

        pool.returnBrokenResource(jedis);
      } catch (JedisConnectionException ex) {
        if (jedis != null) {
          pool.returnBrokenResource(jedis);
        }
      }
    }

    throw new JedisConnectionException("no reachable node in cluster");
  }

    public List<Jedis> queryEffectiveConnection() {
        List<Jedis> retList = new ArrayList<Jedis>();
        List<JedisPool> pools = getShuffledNodesPool();

        for (JedisPool pool : pools) {
            Jedis jedis = null;
            try {
                jedis = pool.getResource();

                if (jedis == null) {
                    continue;
                }
                String result = jedis.ping();
                if (result.equalsIgnoreCase("pong")) {
                    if(isNodeMaster(jedis.clusterNodes())){
                        retList.add(jedis);
                    }
                }else {
                    pool.returnBrokenResource(jedis);
                }

            } catch (JedisConnectionException ex) {
                if (jedis != null) {
                    pool.returnBrokenResource(jedis);
                }
            }
        }
        return retList;
    }

    public static boolean isNodeMaster(String infoOutput) {
        for (String infoLine : infoOutput.split("\n")) {
            if (infoLine.contains("myself")) {
                String sign =  infoLine.split(" ")[2];
                if(sign != null && sign.indexOf("master") > 0 ){
                    return true;
                }
            }
        }
        return false;
    }

  @Override
  public Jedis getConnectionFromSlot(int slot) {
    JedisPool connectionPool = cache.getSlotPool(slot);
    if (connectionPool != null) {
      // It can't guaranteed to get valid connection because of node
      // assignment
      return connectionPool.getResource();
    } else {
      return getConnection();
    }
  }

  private List<JedisPool> getShuffledNodesPool() {
    List<JedisPool> pools = new ArrayList<JedisPool>();
    pools.addAll(cache.getNodes().values());
    Collections.shuffle(pools);
    return pools;
  }

}