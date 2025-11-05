package app.bl;
import java.util.Objects;

public class Keyword {
    private final String name;
    private final double baseWeight;
    public Keyword(String name, double baseWeight){ this.name=name; this.baseWeight=baseWeight; }
    public String name(){ return name; }
    public double base(){ return baseWeight; }
    @Override public boolean equals(Object o){ return o instanceof Keyword k && k.name.equals(name); }
    @Override public int hashCode(){ return Objects.hash(name); }
    @Override public String toString(){ return name; }
}
