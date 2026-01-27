public class TeamBuildConfig {
    public int numTeams;
    public int iterations;
    public int randomness;
    public boolean allowSameSpeciesInTeam;
    public boolean allowSharedTypes;
    public boolean allowSelfFusion;
    public int exhaustiveMode; // 0=Speed, 1=Balanced, 2=Quality, 3=Maximum
    
    public TeamBuildConfig(int teams, int iters, int rand, boolean speciesDupes, 
                           boolean sharedTypes, boolean self, int exhaustive) {
        this.numTeams = teams;
        this.iterations = iters;
        this.randomness = rand;
        this.allowSameSpeciesInTeam = speciesDupes;
        this.allowSharedTypes = sharedTypes;
        this.allowSelfFusion = self;
        this.exhaustiveMode = exhaustive;
    }
}
