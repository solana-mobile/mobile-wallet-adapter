#!/bin/sh
PLANTUML_TMP=`mktemp -d "$RUNNER_TEMP/plantuml.XXXXXX"` || exit 1
gh release download -R plantuml/plantuml -p 'plantuml-[0-9]*[0-9].jar' -D $PLANTUML_TMP || exit 1
PLANTUML_JAR=$(ls $PLANTUML_TMP/plantuml*.jar | head -1)
echo '#!/bin/sh' > "$PLANTUML_TMP/plantuml"
echo "java -jar \"$PLANTUML_JAR\" \"\$1\" \"\$2\"" >> "$PLANTUML_TMP/plantuml"
chmod +x $PLANTUML_TMP/plantuml
echo $PLANTUML_TMP >> $GITHUB_PATH
echo "*** Installed PlantUML to $PLANTUML_TMP"