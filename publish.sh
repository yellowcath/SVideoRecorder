#!/bin/bash
# 默认放在工程根目录下
# array里面填写改工程下所有需要发布的模块，注意按依赖顺序写入，比如library1依赖library2，则library2在前，library1在后
# ------配置区域------
array=(
library 
)
myPath=./releaseNote/


# ------执行区域------
if [ ! -d "$myPath" ];then
    mkdir "$myPath"
fi

version=$(grep "publish_version=" gradle.properties)
version=${version#*publish_version=}

# check version
dot_num=$(echo $version|grep -o '\.'|wc -l)
if [ $dot_num -ne 2 ]
then
   echo "Error!$version not allowed, only two separator is allowed(x.x.x or x.x.x-SNAPSHOT)"
   exit 1;
fi

output="$myPath$version.txt"

count=${#array[@]}
echo '' |tee -a ${output}
echo '------------------------------------------------------------------------------------------------------------------------' |tee -a ${output}
date |tee -a ${output}
echo '------------------------------------------------------------------------------------------------------------------------' |tee -a ${output}
for (( i = 0; i < count; i+=1 )); do
	gradle -q ${array[i]}:uploadArchives |tee -a ${output}
	gradle -q ${array[i]}:dependencies --configuration archives |tee -a ${output}
done
echo '------------------------------------------------------------------------------------------------------------------------' |tee -a ${output}
echo 'END_OF_PUBLISH' |tee -a ${output}
echo '------------------------------------------------------------------------------------------------------------------------' |tee -a ${output}
echo '' |tee -a ${output}
echo '' |tee -a ${output}

