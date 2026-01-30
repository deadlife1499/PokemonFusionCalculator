public class TeamBuildConfig {
    // 0 = Off, 1-99 = Penalty/Bonus Weight, 100 = Hard Requirement
    public final int speciesClauseVal;
    public final int typeClauseVal;
    public final int selfFusionClauseVal;

    public TeamBuildConfig(int speciesClauseVal, int typeClauseVal, int selfFusionClauseVal) {
        this.speciesClauseVal = speciesClauseVal;
        this.typeClauseVal = typeClauseVal;
        this.selfFusionClauseVal = selfFusionClauseVal;
    }
}