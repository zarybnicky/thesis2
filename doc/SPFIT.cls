% ---------------------------------------------------------------------
% Excel@FIT latex template adapted for project practice (PP) papers
% ---------------------------------------------------------------------
%  HISTORY:
%    2015-02-04 - Herout: intial version for Excel@FIT 2015
%    2015-04-03 - Herout: works with [czech] and [slovak] options
%    2016-12-08 - Dytrych: template adapted for KNOT papers
%    2019-09-18
%    2019-12-04 - Veigend: template updated for project practice papers
% ---------------------------------------------------------------------

\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{SPFIT}[05/12/2019, v1.0]
\RequirePackage{etoolbox}
\RequirePackage{ifthen}
\RequirePackage{calc}
\AtEndOfClass{\RequirePackage{microtype}}
\DeclareOption*{\PassOptionsToClass{\CurrentOption}{article}}
\ProcessOptions*
\LoadClass[fleqn,11pt]{article}
\RequirePackage{ifpdf}      % Needed to pick between latex and pdflatex

%----------------------------------------------------------------------
%	PARAMETERS
%----------------------------------------------------------------------
\newif\ifenglish \let\ifenglish\iftrue
\DeclareOption*{}
\ProcessOptions\relax

%----------------------------------------------------------------------
%	FONTS
%----------------------------------------------------------------------

\RequirePackage{times}      % Loads the Times-Roman Fonts
\RequirePackage{mathptmx}   % Loads the Times-Roman Math Fonts

%----------------------------------------------------------------------
%	VARIOUS USEFUL PACKAGES
%----------------------------------------------------------------------
\RequirePackage[T1]{fontenc}
\RequirePackage{amsmath,amsfonts,amssymb}
\RequirePackage{graphicx,xcolor}
\RequirePackage[english]{babel}
\RequirePackage{booktabs}

%----------------------------------------------------------------------
%	MARGINS
%----------------------------------------------------------------------

\RequirePackage[left=2cm,%
                right=2cm,%
				top=2.25cm,%
				bottom=2.25cm,%
				headheight=11pt,%
				a4paper]{geometry}%

%----------------------------------------------------------------------
%	FIGURES AND TABLES CAPTIONS
%----------------------------------------------------------------------

\RequirePackage[labelfont={bf,sf,small},%
                labelsep=period,%
                justification=raggedright]{caption}
\setlength{\abovecaptionskip}{4pt}
\setlength{\belowcaptionskip}{-10pt}

%----------------------------------------------------------------------
%	PAGE HEADER
%----------------------------------------------------------------------

\RequirePackage{fancyhdr}  % Needed to define custom headers/footers
\RequirePackage{lastpage}  % Number of pages in the document
\pagestyle{fancy}          % Enables the custom headers/footers
% Headers
\lhead{}%
\chead{}%
\rhead{\small\sffamily\bfseries\@PaperTitle\  --- \thepage/\pageref{LastPage}}
% Footers
\lfoot{}%
\cfoot{}%
\rfoot{}%
\renewcommand{\headrulewidth}{0pt}% % No header rule
\renewcommand{\footrulewidth}{0pt}% % No footer rule

%----------------------------------------------------------------------
%	SECTION/SUBSECTION/PARAGRAPH SET-UP
%----------------------------------------------------------------------

\RequirePackage[explicit]{titlesec}
\titleformat{\section}
  {\color{white}\large\sffamily\bfseries}
  {}
  {0em}
  {\colorbox{color-blue!100}{\parbox{\dimexpr\linewidth-2\fboxsep\relax}{\arabic{section}. #1}}}
  []
\titleformat{name=\section,numberless}
  {\color{white}\large\sffamily\bfseries}
  {}
  {0em}
  {\colorbox{color-blue!100}{\parbox{\dimexpr\linewidth-2\fboxsep\relax}{#1}}}
  []
\titleformat{\subsection}
  {\color{color-sections}\sffamily\bfseries}
  {\thesubsection}
  {0.5em}
  {#1}
  []
\titleformat{\subsubsection}
  {\sffamily\small\bfseries}
  {\thesubsubsection}
  {0.5em}
  {#1}
  []
\titleformat{\paragraph}[runin]
  {\sffamily\small\bfseries}
  {}
  {0em}
  {#1}
\titlespacing*{\section}{0pc}{3ex \@plus4pt \@minus3pt}{5pt}
\titlespacing*{\subsection}{0pc}{2.5ex \@plus3pt \@minus2pt}{1pt}
\titlespacing*{\subsubsection}{0pc}{2ex \@plus2.5pt \@minus1.5pt}{0pt}
\titlespacing*{\paragraph}{0pc}{1.5ex \@plus2pt \@minus1pt}{10pt}

%----------------------------------------------------------------------
%	TABLEOFCONTENTS SET-UP
%----------------------------------------------------------------------
\newlength{\tocsep}
\setlength\tocsep{1.5pc} % Sets the indentation of the sections in the table of contents
\setcounter{tocdepth}{3} % Three levels in the table of contents section: sections, subsections and subsubsections

\usepackage{titletoc}
\contentsmargin{0cm}
\titlecontents{section}[\tocsep]
  {\addvspace{4pt}\small\bfseries\sffamily}
  {\contentslabel[\thecontentslabel]{\tocsep}}
  {}
  {\hfill\thecontentspage}
  []
\titlecontents{subsection}[\tocsep]
  {\addvspace{2pt}\small\sffamily}
  {\contentslabel[\thecontentslabel]{\tocsep}}
  {}
  {\ \titlerule*[.5pc]{.}\ \thecontentspage}
  []
\titlecontents*{subsubsection}[\tocsep]
  {\footnotesize\sffamily}
  {}
  {}
  {}
  [\ \textbullet\ ]

%----------------------------------------------------------------------
%	MULTIPLE AUTHOR SET
%----------------------------------------------------------------------

\newcount\@authcnt
\newcount\@tmpcnt\@tmpcnt\z@

\def\@affiliation{%
  \ifnum\@tmpcnt<\@authcnt
   \global\advance\@tmpcnt1
    \raggedright \csname @auth\romannumeral\the\@tmpcnt\endcsname\hfill\\%
   \let\next\@affiliation \vskip1pt
  \else
   \let\next\relax
  \fi
\next}

\newcommand{\affiliation}[1]{%
    \global\advance\@authcnt1
    \expandafter\gdef\csname @auth\romannumeral\the\@authcnt\endcsname
    {#1}}


%----------------------------------------------------------------------
%	LIST CONTROL
%----------------------------------------------------------------------

\RequirePackage{enumitem}
%\setlist{nolistsep} % Uncomment to remove spacing between items in lists (enumerate, itemize)
\setlist{noitemsep}


\renewcommand{\@maketitle}{%
\twocolumn[{%
\thispagestyle{empty}%
{\Huge\sffamily\bfseries
    \includegraphics[height=9.5mm]{VUT-FIT-logo-en.png} \hfill
\\[2pt]
\large{\url{https://www.fit.vut.cz}} \hfill \Huge\date \par%
}
% {\raggedright%
% %\includegraphics[width=67mm]{VUT-FIT-logo.pdf}\\[2pt]%
% %\includegraphics[width=67mm]{ExcelAtFIT-logo.pdf}%
% \href{http://fit.vut.cz}{\includegraphics[height=9.5mm]{VUT-FIT-logo.pdf}}\hfill%
% \Huge{Project practice} \hfill
% \\[2pt] \hfill \Huge{\color{color-sections}{\@PPYear}}\par}%
\vskip20pt%
{\raggedright\color{color-sections}\sffamily\bfseries\fontsize{20}{25}\selectfont \@title\par}%
\vskip10pt%
{\raggedright\color{color-sections}\sffamily\fontsize{12}{16}\selectfont  \myauthor\par}%
\vskip18pt%
\fcolorbox{color-blue}{white}{%
\parbox{\textwidth-2\fboxsep-2\fboxrule}{\centering%
\colorbox{color2!8}{%
\parbox{\textwidth-4\fboxsep-2\fboxrule}{%
\sffamily\textbf{\abstractname}\\\myabstract%
}%
}%
\vskip4pt%
\begingroup%
\raggedright\sffamily\small%
\footnotesize\@affiliation\par%
\endgroup%%
}%
}%
\vskip25pt%
}]%
}%

%----------------------------------------------------------------------
%	REFERENCES
%----------------------------------------------------------------------

% Remove brackets from numbering in List of References
\renewcommand{\@biblabel}[1]{[{\color{color-ref}#1}]}
%\setlength{\bibitemsep}{0cm}
\let\oldbibliography\thebibliography
\renewcommand{\thebibliography}[1]{%
\addcontentsline{toc}{section}{\refname}%
\oldbibliography{#1}%
\setlength\itemsep{0pt}}%




%%% Make figure placement a little more predictable.
% We trust the user to move figures if this results
% in ugliness.
% Minimize bad page breaks at figures
\renewcommand{\textfraction}{0.01}
\renewcommand{\floatpagefraction}{0.99}
\renewcommand{\topfraction}{0.99}
\renewcommand{\bottomfraction}{0.99}
\renewcommand{\dblfloatpagefraction}{0.99}
\renewcommand{\dbltopfraction}{0.99}
\setcounter{totalnumber}{99}
\setcounter{topnumber}{99}
\setcounter{bottomnumber}{99}




%----------------------------------------------------------------------
%	ADDED STUFF FOR Excel@FIT
%----------------------------------------------------------------------
\setlength{\columnsep}{0.55cm} % Distance between the two columns of text
\setlength{\fboxrule}{0.75pt} % Width of the border around the abstract

\definecolor{color-sections}{RGB}{3,5,80} % Color of the article title and sections
%\definecolor{color-sections}{RGB}{3,5,80} % Color of the article title and sections
%\definecolor{color-sections}{RGB}{10,20,120} % Color of the article title and sections
\definecolor{color-links}{RGB}{10,15,160} % Color of the article title and sections
%\definecolor{color-links}{RGB}{170,15,10} % Color of the article title and sections
\definecolor{color-blue}{RGB}{8,83,252} % Color of the article title and sections
\definecolor{color-orange}{RGB}{246,135,18} % Color of the article title and sections
\definecolor{color2}{RGB}{20,20,20} % Color of the boxes behind the abstract and headings
\definecolor{color-ref}{RGB}{223,52,30}
\definecolor{color-lineno}{RGB}{90,140,160}

%\definecolor{color-sections}{RGB}{66,139,202} % Color of the article title and sections


\usepackage{hyperref} % Required for hyperlinks
\hypersetup{hidelinks,unicode,colorlinks,breaklinks=true,urlcolor=color-links,citecolor=color-ref,linkcolor=color-ref,bookmarksopen=false,pdftitle={Title},pdfauthor={Author}}

\setcounter{tocdepth}{1}

\pagestyle{empty} % Removes page numbering from the first page

\newcommand{\startdocument}{%
	\flushbottom%
	\maketitle%
%	\tableofcontents%
	\thispagestyle{empty}%
	\ifPPFinal\else\setpagewiselinenumbers\linenumbers\renewcommand\linenumberfont{\scriptsize\color{color-lineno}}\fi%
	}

\renewcommand{\tabcolsep}{1.0mm}
\renewcommand\arraystretch{0.90}

\newif\ifPPFinal
\PPFinalfalse
\def\PPFinalCopy{\global\PPFinaltrue}

\usepackage{regexpatch}
\xpatchparametertext\@@@cmidrule{-}{\cA-}{}{}
\xpatchparametertext\@cline{-}{\cA-}{}{}

%--------------------------------------------------------
%	USEFUL PREPARATIONS
%--------------------------------------------------------

\graphicspath{{img/}{plots/}}

\usepackage{blindtext}
\newcommand{\sgn}{\operatorname{sgn}}

\usepackage{booktabs}

\graphicspath{{img/}{plots/}}

\newcommand{\todo}[1]{\textcolor{red}{\textbf{[[#1]]}}}
\newcommand{\phony}[1]{\textcolor{gray}{#1}}
\newcommand{\blind}[1]{\textcolor{gray}{\Blindtext[#1]}}

\usepackage[switch]{lineno}
