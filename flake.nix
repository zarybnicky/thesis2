{
  inputs.nixpkgs.url = github:NixOS/nixpkgs/release-20.09;
  inputs.lambdapi = { url = github:ilya-klyuchnikov/lambdapi/master; flake =  false; };
  inputs.smalltt = { url = github:zarybnicky/smalltt/master; flake = false; };
  inputs.normbench = { url = github:zarybnicky/normalization-bench/master; flake = false; };
  inputs.lean.url = github:leanprover/lean4;
  inputs.lean.inputs.nixpkgs.follows = "nixpkgs";
  inputs.lean.inputs.nixpkgs-vscode.follows = "nixpkgs";

  outputs = { self, lambdapi, smalltt, lean, normbench, nixpkgs }: let
    inherit (pkgs.nix-gitignore) gitignoreSourcePure;
    getSrc = dir: gitignoreSourcePure [./.gitignore] dir;
    pkgs = import nixpkgs {
      system = "x86_64-linux";
      overlays = [ self.overlay ];
      config.allowUnfree = true;
    };
    compiler = "ghc884";
    hsPkgs = pkgs.haskell.packages.${compiler};
    graal = pkgs.graalvm11-ee;
  in {
    overlay = final: prev: {
      inherit (prev.callPackage ./dep/graalvm-ee.nix {}) graalvm11-ee;
      haskell = prev.haskell // {
        packageOverrides = prev.lib.composeExtensions (prev.haskell.packageOverrides or (_: _: {})) (hself: hsuper: {
          lph = hself.callCabal2nix "lph" lambdapi {};
          smalltt = hself.callCabal2nix "smalltt" smalltt {};
          primdata = prev.haskell.lib.dontHaddock (hself.callCabal2nix "primdata" "${smalltt}/primdata" {});
          dynamic-array = hself.callCabal2nix "dynamic-array" "${smalltt}/dynamic-array" {};
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
          makeWrapper ${graal}/bin/java $out/bin/gradle \
            --set JAVA_HOME ${graal} \
            --add-flags "-classpath $gradle_launcher_jar org.gradle.launcher.GradleMain"
        '';
        buildInputs = [ final.unzip graal final.makeWrapper ];
      };
      antlr4 = prev.antlr4.override { jre = graal; };
      visualvm = prev.visualvm.override { jdk = graal; };
      seafoam = pkgs.bundlerApp {
        pname = "seafoam";
        gemfile = ./dep/seafoam/Gemfile;
        lockfile = ./dep/seafoam/Gemfile.lock;
        gemset = ./dep/seafoam/gemset.nix;
        exes = ["seafoam"];
        buildInputs = [pkgs.makeWrapper];
        postBuild = "wrapProgram $out/bin/seafoam --prefix PATH : ${pkgs.lib.makeBinPath [ pkgs.graphviz ]}";
      };
      dotnet-sdk-lts = prev.dotnet-sdk.overrideAttrs (
        old: rec {
          version = "3.1.102";
          src = prev.fetchurl {
            url = "https://dotnetcli.azureedge.net/dotnet/Sdk/${version}/dotnet-sdk-${version}-linux-x64.tar.gz";
            sha256 = "e03ceeb5beaf7c228bd8dcbf7712cf12f5ccbfcd6d426afff78bfbd4524ff558";
          };
        }
      );
    };
    packages.x86_64-linux = { inherit (pkgs) normbench-fsharp; };

    devShell.x86_64-linux = hsPkgs.shellFor rec {
      withHoogle = false;
      packages = p: [ p.smalltt p.dynamic-array p.lph ];
      LD_LIBRARY_PATH = pkgs.lib.strings.makeLibraryPath buildInputs;
      FONTCONFIG_FILE = pkgs.makeFontsConf { fontDirectories = [pkgs.lmodern] ++ pkgs.texlive.tex-gyre.pkgs; };
      buildInputs = [
        hsPkgs.cabal-install
        hsPkgs.hie-bios
        hsPkgs.haskell-language-server
        pkgs.kotlin-language-server
        graal
        pkgs.gradle
        pkgs.hyperfine
        pkgs.antlr4
        pkgs.visualvm
        (pkgs.agda.withPackages (p: [ p.standard-library ]))
        (pkgs.idrisPackages.with-packages (with pkgs.idrisPackages; [ contrib pruviloj ]))
        lean.defaultPackage.x86_64-linux
        pkgs.coq
        pkgs.dotnet-sdk-lts
        pkgs.ocaml
        pkgs.sbt
        pkgs.seafoam
      ];
    };
  };
}
