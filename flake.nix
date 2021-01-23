{
  inputs.nixpkgs.url = github:NixOS/nixpkgs/master;

  outputs = { self, nixpkgs }: let
    inherit (pkgs.nix-gitignore) gitignoreSourcePure;
    pkgs = import nixpkgs {
      system = "x86_64-linux";
      overlays = [ self.overlay ];
      config.allowUnfree = true;
    };
  in {
    overlay = final: prev: {
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
    };

    devShell.x86_64-linux = pkgs.mkShell {
      inputsFrom = [];
      buildInputs = [
        pkgs.kotlin-language-server
        pkgs.graalvm11-ee
        pkgs.gradle
      ];
    };
  };
}
