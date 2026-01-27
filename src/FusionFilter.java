import java.util.List;
import java.util.stream.Collectors;

public class FusionFilter {
    String typeConstraint;
    String abilityConstraint;
    int minHP, minAtk, minDef, minSpa, minSpd, minSpe, minBST;
    
    public FusionFilter() {} 
    
    public List<Fusion> apply(List<Fusion> input) {
        return input.stream().filter(f -> {
            if (minHP > 0 && f.hp < minHP) return false;
            if (minAtk > 0 && f.atk < minAtk) return false;
            if (minDef > 0 && f.def < minDef) return false;
            if (minSpa > 0 && f.spa < minSpa) return false;
            if (minSpd > 0 && f.spd < minSpd) return false;
            if (minSpe > 0 && f.spe < minSpe) return false;
            if (minBST > 0 && f.bst < minBST) return false;
            
            if (typeConstraint != null && !typeConstraint.isEmpty()) {
                if (!f.typing.toLowerCase().contains(typeConstraint.toLowerCase())) return false;
            }
            
            if (abilityConstraint != null && !abilityConstraint.isEmpty()) {
                if (!f.chosenAbility.toLowerCase().contains(abilityConstraint.toLowerCase())) return false;
            }
            
            return true;
        }).collect(Collectors.toList());
    }
}