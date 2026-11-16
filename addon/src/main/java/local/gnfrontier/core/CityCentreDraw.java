package local.gnfrontier.core;

/** The installed normal-city draw, without allocating a Random and AtomicLong per coordinate. */
public final class CityCentreDraw {
    private static final long MULTIPLIER=0x5DEECE66DL, MASK=(1L<<48)-1;
    private CityCentreDraw(){}
    public static double draw(int x,int z){
        long seed=((z*797003437L+x*295075153L)^MULTIPLIER)&MASK;
        seed=(seed*MULTIPLIER+11)&MASK;
        long high=seed>>>22;
        seed=(seed*MULTIPLIER+11)&MASK;
        return ((high<<27)+(seed>>>21))*0x1.0p-53;
    }
}
