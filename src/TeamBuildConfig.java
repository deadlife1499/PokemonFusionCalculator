public class TeamBuildConfig {
    public final boolean allowSameSpeciesInTeam;
    public final boolean allowSharedTypes;
    public final boolean allowSelfFusion;

    public TeamBuildConfig(boolean allowSameSpeciesInTeam, boolean allowSharedTypes, boolean allowSelfFusion) {
        this.allowSameSpeciesInTeam = allowSameSpeciesInTeam;
        this.allowSharedTypes = allowSharedTypes;
        this.allowSelfFusion = allowSelfFusion;
    }
}