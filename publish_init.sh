#!/bin/bash

# ------------------ #
# @author mr         #
# @version 1.0.1     #
# ------------------ #

# 判断内容是否存在，不存在则添加相应内容
# s1=filename, s2=contentToCheckIfExist, s3=appendContent, s4=appendPosition
appendIfNotExist() {
    touch $1
    if checkIfNotExist "$1" "$2"
    then
        echo 'do write file'
        if [ -n "$4" ]
        then
            sed -i '' "/$4/a\\
            $3\\
            " $1
        else
            echo >> $1
            echo $3 >> $1
        fi
    else
        echo 'do nothing'
    fi
}

# 判断内容是否存在，不存在则要求从终端输入
# s1=filename, s2=contentToCheckIfExist
checkIfNotExist() {
    touch $1
    if grep "$2" $1 >/dev/null
    then
        return 1
    else
        return 0
    fi
}

# 定义工具版本
_publish_tool_version=1.0.1

# 检查输入参数是否合法，至少需要一个library
if [ $# -lt 1 ] ; then
echo "USAGE: publish_init.sh LIBRARY_NAME...(传入需要初始化的发布library)"
echo " e.g.: publish_init.sh library3 library2 library1(注意，被依赖的库顺序需要放在前面，如library1依赖library2，则library2在前)"
exit 1;
fi

echo --START--
# 提示输入，获取所有输入内容
if checkIfNotExist "gradle.properties" "publish_group="
then
    echo "输入发布域名(publish_group, eg: us.pinguo.tools):"
    read _group
else
    echo 'publish_group exist'
fi

if checkIfNotExist "gradle.properties" "publish_version="
then
    echo "输入发布版本号(publish_version, eg: 1.0.0-SNAPSHOT):"
    read _version
else
    echo 'publish_version exist'
fi

if checkIfNotExist "local.properties" "MAVEN_USERNAME="
then
    echo "输入maven用户名(MAVEN_USERNAME, eg: marui):"
    read _username
else
    echo 'MAVEN_USERNAME exist'
fi

if checkIfNotExist "local.properties" "MAVEN_PASSWORD="
then
    echo "输入maven密码(MAVEN_PASSWORD, eg: ******):"
    read -s _password
else
    echo 'MAVEN_PASSWORD exist'
fi

# 添加plugin classpath
echo '-----Start append classpath in build.gradle-----'
appendIfNotExist "build.gradle" "classpath 'us.pinguo.tool:publish:" "classpath 'us.pinguo.tool:publish:$_publish_tool_version'" "dependencies {"
# 更新plugin版本号
sed -i '' "s/'us.pinguo.tool:publish:*.*.*'/'us.pinguo.tool:publish:$_publish_tool_version'/" build.gradle
# 添加配置group
echo '-----Start append group in gradle.properties-----'
appendIfNotExist "gradle.properties" "publish_group=" "publish_group=$_group"
# 添加配置version
echo '-----Start append version in gradle.properties-----'
appendIfNotExist "gradle.properties" "publish_version=" "publish_version=$_version"
# 添加配置username
echo '-----Start append username in local.properties-----'
appendIfNotExist "local.properties" "MAVEN_USERNAME=" "MAVEN_USERNAME=$_username"
# 添加配置password
echo '-----Start append password in local.properties-----'
appendIfNotExist "local.properties" "MAVEN_PASSWORD=" "MAVEN_PASSWORD=$_password"

# 获取所有参数
libraries=""
for args in $@;do
    libraries+=$args" "
    appendIfNotExist "$args/build.gradle" "apply plugin: 'us.pinguo.tool.publish'" "apply plugin: 'us.pinguo.tool.publish'" "apply plugin: 'com.android.library'"

    if checkIfNotExist "$args/build.gradle" "publish_id" == 1
    then
        echo "输入$args 的发布ID(publish_id, eg: camerasdk-core):"
        read _id
        appendIfNotExist "$args/build.gradle" "publish_id" "publish { publish_id '$_id' }" "apply plugin: 'us.pinguo.tool.publish'"
    else
        echo "$args publish_id exist"
    fi
done

# 生成发布脚本
echo -----生成发布执行脚本-----

echo """#!/bin/bash
# 默认放在工程根目录下
# array里面填写改工程下所有需要发布的模块，注意按依赖顺序写入，比如library1依赖library2，则library2在前，library1在后
# ------配置区域------
array=(
$libraries
)
myPath=./releaseNote/


# ------执行区域------
if [ ! -d \"\$myPath\" ];then
    mkdir \"\$myPath\"
fi

version=\$(grep \"publish_version=\" gradle.properties)
version=\${version#*publish_version=}

# check version
dot_num=\$(echo \$version|grep -o '\.'|wc -l)
if [ \$dot_num -ne 2 ]
then
   echo \"Error!\$version not allowed, only two separator is allowed(x.x.x or x.x.x-SNAPSHOT)\"
   exit 1;
fi

output=\"\$myPath\$version.txt\"

count=\${#array[@]}
echo '' |tee -a \${output}
echo '------------------------------------------------------------------------------------------------------------------------' |tee -a \${output}
date |tee -a \${output}
echo '------------------------------------------------------------------------------------------------------------------------' |tee -a \${output}
for (( i = 0; i < count; i+=1 )); do
	./gradlew -q \${array[i]}:uploadArchives |tee -a \${output}
	./gradlew -q \${array[i]}:dependencies --configuration archives |tee -a \${output}
done
echo '------------------------------------------------------------------------------------------------------------------------' |tee -a \${output}
echo 'END_OF_PUBLISH' |tee -a \${output}
echo '------------------------------------------------------------------------------------------------------------------------' |tee -a \${output}
echo '' |tee -a \${output}
echo '' |tee -a \${output}
""" > publish.sh
chmod +x publish.sh
echo -----生成发布脚本成功, 执行publish.sh可以发布组件-----
echo --FINISH--