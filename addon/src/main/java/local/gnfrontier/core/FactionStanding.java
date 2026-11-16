package local.gnfrontier.core;

/** One interpretation of GN reputation/alert, shared by diplomacy, beds and bounties. */
public final class FactionStanding {
    public enum Relation { ENEMY, NEUTRAL, ALLY }
    private FactionStanding() {}
    public static Relation relation(Economy.State account,int faction){
        if(faction<0||faction>1)throw new IllegalArgumentException("Not a regular military faction");
        return account.alert[faction]>=30||account.reputation[faction]<=-20?Relation.ENEMY
                :account.reputation[faction]>=30?Relation.ALLY:Relation.NEUTRAL;
    }
}
