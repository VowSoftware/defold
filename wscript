#! /usr/bin/env python

DDF_MAJOR_VERSION=1
DDF_MINOR_VERSION=0

VERSION='%d.%d' % (DDF_MAJOR_VERSION, DDF_MINOR_VERSION)
APPNAME='ddf'

srcdir = '.'
blddir = 'build'

import os, sys, re
sys.path = ["src"] + sys.path
import waf_ddf, waf_dynamo

def init():
    pass

def set_options(opt):
    opt.sub_options('src')
    opt.tool_options('compiler_cxx')

def configure(conf):
    # Replace version number in python file.
    ddfc_py_str = ddfc_py_str_orig = open('src/ddfc.py', 'rb').read()
    ddfc_py_str = re.sub('DDF_MAJOR_VERSION=(\d*)', 'DDF_MAJOR_VERSION=%d' % DDF_MAJOR_VERSION, ddfc_py_str)
    ddfc_py_str = re.sub('DDF_MINOR_VERSION=(\d*)', 'DDF_MINOR_VERSION=%d' % DDF_MINOR_VERSION, ddfc_py_str)
    if ddfc_py_str != ddfc_py_str_orig:
        open('src/ddfc.py', 'wb').write(ddfc_py_str)

    # Create config.h with version numbers
    conf.define('DDF_MAJOR_VERSION', DDF_MAJOR_VERSION)
    conf.define('DDF_MINOR_VERSION', DDF_MINOR_VERSION)
    conf.write_config_header('config.h')

    conf.check_tool('compiler_cxx')
    conf.check_tool('java')

    conf.sub_config('src')

    conf.find_program('ddfc_cxx', var='DDFC_CXX', path_list = [os.path.abspath('src')], mandatory = True)
    conf.find_program('ddfc_java', var='DDFC_JAVA', path_list = [os.path.abspath('src')], mandatory = True)

    waf_dynamo.configure(conf)

    conf.env['PROTOBUF_JAR'] = conf.env.DYNAMO_HOME + '/ext/share/java/protobuf-java-2.3.0.jar'

    if sys.platform == "darwin":
        platform = "darwin"
    elif sys.platform == "linux2":
        platform = "linux"
    elif sys.platform == "win32":
        platform = "win32"
    else:
        conf.fatal("Unable to determine platform")

    if platform == 'win32':
        conf.env.append_value('CPPPATH', "../src/win32")

    conf.env['STATICLIB_DLIB'] = 'dlib'
    conf.env['LIB_PROTOBUF'] = 'protobuf'
    conf.env['LIB_GTEST'] = 'gtest'

def build(bld):
    # We need to add default/src/ddf to PYTHONPATH here. (ddf_extensions_pb2.py and plugin_pb2.py)
    # Only required 'in' ddf-lib.
    python_path = os.environ.get('PYTHONPATH', '')
    os.environ['PYTHONPATH'] = python_path + os.pathsep + 'default/src/ddf'
    bld.add_subdirs('src')

def shutdown():
    import Options, Build

    waf_dynamo.run_gtests(valgrind = True)

    if Options.commands['build']:
        dynamo_home = Build.bld.get_env()['DYNAMO_HOME']
        cp = os.pathsep.join([dynamo_home + '/ext/share/java/protobuf-java-2.3.0.jar',
                              dynamo_home + '/ext/share/java/junit-4.6.jar',
                              'build/default/src/java_test',
                              'build/default/src/test/generated',
                              'build/default/src/java',
                              'build/default/src/ddf/generated'])
        cmd = """
"%s" -cp %s org.junit.runner.JUnitCore com.dynamo.ddf.test.DDFLoaderTest
""" % (Build.bld.get_env()['JAVA'][0], cp)
        ret = os.system('%s' % cmd)
        if ret != 0:
            sys.exit(ret)

