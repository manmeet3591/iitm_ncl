import java.util.*;

public class Cache {

  /* A simple LRU Cache.
  ** As with any class that can be stored in 
  ** a Hashtable, a class that uses Cache must
  ** implement the hashCode method and the 
  ** equals method.
  *************************************/

  private Hashtable ht;
  private LinkedList list;
  private long maxSize;

    /******************
    ** Constructors
    ******************/

  Cache() {
    // build a cache of max size 64.
    list = new LinkedList();
    ht = new Hashtable(64);
    maxSize = 64;
  }

  Cache(int sz) {
    list = new LinkedList();
    ht = new Hashtable(sz);
    maxSize = sz;
  }

  /*******************
  ** Methods
  *******************/

  public long maxSize() {
    return maxSize;
  }

  public long size() {
    return list.size();
  }

  public void clear() {
    list.clear();
    ht.clear();
  }

  public Object put(Object key, Object value) {
    if (list.size() == maxSize) {
      // at capacity.  Remove first item.
      ht.remove(list.removeFirst());
    }
    list.add(key);
    return ht.put(key, value);
  }

  public Object get(Object key) {
    return ht.get(key);
  }
      
}


