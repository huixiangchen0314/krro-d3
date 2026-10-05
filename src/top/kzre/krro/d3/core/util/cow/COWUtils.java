package top.kzre.krro.d3.core.util.cow;

public final class COWUtils {
    private COWUtils() {}

   public static <T extends CopyOnWrite<T> >  T sharedNullable(CopyOnWrite<T> cow){
       return cow == null ? null : cow.shared();
   }
}
