{
  inputs.nixpkgs.url = github:NixOS/nixpkgs/master;
  inputs.lambdapi.url = github:ilya-klyuchnikov/lambdapi/master;
  inputs.lambdapi.flake = false;

  outputs = { self, lambdapi, nixpkgs }: let
    inherit (pkgs.nix-gitignore) gitignoreSourcePure;
    getSrc = dir: gitignoreSourcePure [./.gitignore] dir;
    pkgs = import nixpkgs {
      system = "x86_64-linux";
      overlays = [ self.overlay ];
      config.allowUnfree = true;
    };
    compiler = "ghc884";
    hsPkgs = pkgs.haskell.packages.${compiler};
  in {
    overlay = final: prev: {
      haskell = prev.haskell // {
        packageOverrides = prev.lib.composeExtensions (prev.haskell.packageOverrides or (_: _: {})) (hself: hsuper: {
          lph = hself.callCabal2nix "lph" lambdapi {};
        });
      };

      kotlin-language-server = final.stdenv.mkDerivation rec {
        pname = "kotlin-language-server";
        version = "0.7.0";
        src = final.fetchzip {
          url = "https://github.com/fwcd/kotlin-language-server/releases/download/${version}/server.zip";
          sha256 = "1nsfird6mxzi2cx6k2dlvlsn3ipdf4l1grd4iwz42y3ihm8drgpa";
        };
        nativeBuildInputs = [ final.makeWrapper ];
        installPhase = ''
          install -D $src/bin/kotlin-language-server -t $out/bin
          cp -r $src/lib $out/lib
          wrapProgram $out/bin/kotlin-language-server --prefix PATH : ${final.jre}/bin
       '';
      };
      gradle = final.stdenv.mkDerivation rec {
        name = "gradle-6.7";
        nativeVersion = "0.22-milestone-8";
        src = final.fetchurl {
          url = "https://services.gradle.org/distributions/${name}-bin.zip";
          sha256 = "1i6zm55wzy13wvvmf3804b0rs47yrqqablf4gpf374ls05cpgmca";
        };
        dontBuild = true;

        installPhase = ''
          mkdir -pv $out/lib/gradle/
          cp -rv lib/ $out/lib/gradle/
          gradle_launcher_jar=$(echo $out/lib/gradle/lib/gradle-launcher-*.jar)
          test -f $gradle_launcher_jar
          makeWrapper ${final.graalvm11-ee}/bin/java $out/bin/gradle \
            --set JAVA_HOME ${final.graalvm11-ee} \
            --add-flags "-classpath $gradle_launcher_jar org.gradle.launcher.GradleMain"
        '';
        buildInputs = [ final.unzip final.graalvm11-ee final.makeWrapper ];
      };
    };

    devShell.x86_64-linux = hsPkgs.shellFor {
      withHoogle = false;
      packages = p: [];# p.lph ];
      buildInputs = [
        hsPkgs.cabal-install
        hsPkgs.hie-bios
        hsPkgs.haskell-language-server
        hsPkgs.stan
        pkgs.kotlin-language-server
        pkgs.graalvm11-ee
        pkgs.gradle
        (pkgs.antlr4.override { jre = pkgs.graalvm11-ee; })
      ];
    };
  };
}
