{
  crabstone = {
    dependencies = ["ffi"];
    groups = ["default"];
    platforms = [];
    source = {
      remotes = ["https://rubygems.org"];
      sha256 = "1643lz2psdj2n6xp6w6m4j3n5y4r3rpj0kpng6i9xcwki1hd497m";
      type = "gem";
    };
    version = "4.0.3";
  };
  ffi = {
    groups = ["default"];
    platforms = [];
    source = {
      remotes = ["https://rubygems.org"];
      sha256 = "0nq1fb3vbfylccwba64zblxy96qznxbys5900wd7gm9bpplmf432";
      type = "gem";
    };
    version = "1.15.0";
  };
  seafoam = {
    dependencies = ["crabstone"];
    groups = ["default"];
    platforms = [];
    source = {
      remotes = ["https://rubygems.org"];
      sha256 = "02hf3pq62b5j3cc3szz18lfsfycw93v3r1b9nv5si3zxzrbq7x9f";
      type = "gem";
    };
    version = "0.7";
  };
}